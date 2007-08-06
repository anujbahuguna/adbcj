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
package edu.byu.cs.adbcj.support;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import edu.byu.cs.adbcj.DbException;

public class ConcurrentFutureProxy<T> extends AbstractDbFutureListenerSupport<T> {

	private Future<T> future;
	
	public ConcurrentFutureProxy() {
		// Default constructor
	}
	
	public ConcurrentFutureProxy(Future<T> future) {
		this.future = future;
	}

	public T get() throws DbException, InterruptedException {
		try {
			return future.get();
		} catch (ExecutionException e) {
			throw new DbException(e);
		}
	}

	public T get(long timeout, TimeUnit unit) throws DbException, InterruptedException, TimeoutException {
		try {
			return future.get(timeout, unit);
		} catch (ExecutionException e) {
			throw new DbException(e);
		}
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	public boolean isCancelled() {
		return future.isCancelled();
	}
	
	@Override
	public boolean isDone() {
		return future.isDone();
	}

	public Future<T> getFuture() {
		return future;
	}

	public void setFuture(Future<T> future) {
		this.future = future;
	}
	
}
