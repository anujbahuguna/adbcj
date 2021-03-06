/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj.postgresql;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.adbcj.Connection;
import org.adbcj.ConnectionManager;
import org.adbcj.DbException;
import org.adbcj.DbFuture;
import org.adbcj.DbSessionClosedException;
import org.adbcj.DbSessionFuture;
import org.adbcj.PreparedStatement;
import org.adbcj.Result;
import org.adbcj.ResultEventHandler;
import org.adbcj.postgresql.PgConnectionManager.PgConnectFuture;
import org.adbcj.postgresql.frontend.AbstractFrontendMessage;
import org.adbcj.postgresql.frontend.BindMessage;
import org.adbcj.postgresql.frontend.DescribeMessage;
import org.adbcj.postgresql.frontend.ExecuteMessage;
import org.adbcj.postgresql.frontend.FrontendMessage;
import org.adbcj.postgresql.frontend.ParseMessage;
import org.adbcj.support.AbstractDbSession;
import org.apache.mina.common.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgConnection extends AbstractDbSession implements Connection {
	
	private final Logger logger = LoggerFactory.getLogger(PgConnection.class);

	private final PgConnectionManager connectionManager;
	private final PgConnectFuture connectFuture;
	private final IoSession session;
	// TODO Determine if we really need to distinguish frontend and backend charsets
	// TODO Make frontend charset configurable
	private final Charset frontendCharset = Charset.forName("UTF-8");
	// TODO Update backendCharset based on what backend returns
	private Charset backendCharset = Charset.forName("US-ASCII");

	private Request<Void> closeRequest; // Access synchronized on lock

	private volatile int pid;
	private volatile int key;
	
	// Constant Messages
	private static final ExecuteMessage DEFAULT_EXECUTE = new ExecuteMessage();
	private static final BindMessage DEFAULT_BIND = new BindMessage();
	private static final DescribeMessage DEFAULT_DESCRIBE = DescribeMessage.createDescribePortalMessage(null);
	
	public PgConnection(PgConnectionManager connectionManager, PgConnectFuture connectFuture, IoSession session) {
		this.connectionManager = connectionManager;
		this.connectFuture = connectFuture;
		this.session = session;
		//setPipeliningEnabled(false);
	}
	
	public ConnectionManager getConnectionManager() {
		return connectionManager;
	}

	public DbFuture<Void> ping() {
		// TODO Implement Postgresql ping
		throw new IllegalStateException();
	}

	@Override
	protected void checkClosed() {
		if (isClosed()) {
			throw new DbSessionClosedException(this, "This connection has been closed");
		}
	}

	public DbSessionFuture<Void> close(boolean immediate) throws DbException {
		// TODO PgConnection.close(boolean) is almost identical to MySQL close method, generify this
		
		// If the connection is already closed, return existing close future
		synchronized (lock) {
			if (isClosed()) {
				if (closeRequest == null) {
					closeRequest = new Request<Void>() {
						@Override
						public void execute() throws Exception {
							// Do nothing since close has already occurred
						}
						@Override
						public String toString() {
							return "Connection closed";
						}
					};
					closeRequest.setResult(null);
				}
			} else {
				if (immediate) {
					logger.debug("Executing immediate close");
					// If the close is immediate, cancel pending requests and send request to server
					cancelPendingRequests(true);
					session.write(FrontendMessage.TERMINATE);
					closeRequest = new Request<Void>() {
						@Override
						protected boolean cancelRequest(boolean mayInterruptIfRunning) {
							// Immediate close can not be cancelled
							return false;
						}
						@Override
						public void execute() throws Exception {
							// Do nothing, close message has already been sent
						}
						@Override
						public String toString() {
							return "Immediate close";
						}
					};
				} else {
					// If the close is NOT immediate, schedule the close
					closeRequest = new Request<Void>() {
						@Override
						public boolean cancelRequest(boolean mayInterruptIfRunning) {
							logger.debug("Cancelling close");
							unclose();
							return true;
						}
						@Override
						public void execute() {
							logger.debug("Sending TERMINATE to server (Request queue size: {})", requestQueue.size());
							session.write(FrontendMessage.TERMINATE);
						}
						@Override
						public boolean isPipelinable() {
							return false;
						}
						@Override
						public String toString() {
							return "Deferred close";
						}
					};
					enqueueRequest(closeRequest);
				}
			}
			return closeRequest;
		}
	}

	private void unclose() {
		synchronized (lock) {
			logger.debug("Unclosing");
			this.closeRequest = null;
		}
	}
	
	public boolean isClosed() throws DbException {
		synchronized (lock) {
			return closeRequest != null || session.isClosing();
		}
	}
	
	public <T> DbSessionFuture<T> executeQuery(final String sql, ResultEventHandler<T> eventHandler, T accumulator) {
		checkClosed();
		Request<T> request = new Request<T>(eventHandler, accumulator) {
			@Override
			public void execute() throws Exception {
				logger.debug("Issuing query: {}", sql);
				
				ParseMessage parse = new ParseMessage(sql);
				session.write(new AbstractFrontendMessage[] {
					parse,
					DEFAULT_BIND,
					DEFAULT_DESCRIBE,
					DEFAULT_EXECUTE,
					FrontendMessage.SYNC,
				});
			}
			@Override
			public String toString() {
				return "SELECT request: " + sql;
			}
		};
		return enqueueTransactionalRequest(request);
	}

	public DbSessionFuture<Result> executeUpdate(final String sql) {
		checkClosed();
		return enqueueTransactionalRequest(new Request<Result>() {
			@Override
			public void execute() throws Exception {
				logger.debug("Issuing update query: {}", sql);
				
				ParseMessage parse = new ParseMessage(sql);
				session.write(new AbstractFrontendMessage[] {
					parse,
					DEFAULT_BIND,
					DEFAULT_DESCRIBE,
					DEFAULT_EXECUTE,
					FrontendMessage.SYNC
				});
			}
			
			@Override
			public String toString() {
				return "Update request: " + sql; 
			}
		});
	}

	public DbSessionFuture<PreparedStatement> prepareStatement(String sql) {
		// TODO Implement prepareStatement
		throw new IllegalStateException();
	}

	public DbSessionFuture<PreparedStatement> prepareStatement(Object key, String sql) {
		// TODO Implement prepareStatement
		throw new IllegalStateException();
	}
	
	// ******** Transaction methods ***********************************************************************************
	
	private final AtomicLong statementCounter = new AtomicLong();
	private final Map<String, String> statementCache = Collections.synchronizedMap(new HashMap<String, String>());
	
	@Override
	protected void sendBegin() {
		executeStatement("BEGIN");
	}
	
	@Override
	protected void sendCommit() {
		executeStatement("COMMIT");
	}
	
	@Override
	protected void sendRollback() {
		executeStatement("ROLLBACK");
	}
	
	private void executeStatement(String statement) {
		String statementId = statementCache.get(statement);
		if (statementId == null) {
			long id = statementCounter.incrementAndGet();
			statementId = "S_" + id;
			
			ParseMessage parseMessage = new ParseMessage(statement, statementId);
			session.write(parseMessage);

			statementCache.put(statement, statementId);
		}
		session.write(new AbstractFrontendMessage[] {
				new BindMessage(statementId),
				DEFAULT_EXECUTE,
				FrontendMessage.SYNC
		});
	}
	
	// ================================================================================================================
	//
	// Non-API methods
	//
	// ================================================================================================================
	
	public Charset getFrontendCharset() {
		return frontendCharset;
	}
	
	public Charset getBackendCharset() {
		return backendCharset;
	}

	public Request<Void> getCloseRequest() {
		return closeRequest;
	}
	
	public PgConnectFuture getConnectFuture() {
		return connectFuture;
	}
	
	@Override
	protected <E> void enqueueRequest(Request<E> request) {
		super.enqueueRequest(request);
	}
	
	@Override
	public <E> Request<E> getActiveRequest() {
		return super.getActiveRequest();
	}
	
	public int getPid() {
		return pid;
	}

	public void setPid(int pid) {
		this.pid = pid;
	}

	public int getKey() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
	}
	
}
