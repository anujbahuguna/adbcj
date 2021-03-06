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
package org.adbcj.support;

import org.adbcj.ResultEventHandler;

// TODO Determine if we can make request extend DefaultDbFuture to minimize object creation and redundancy
public abstract class Request<T> {
	
	private DefaultDbSessionFuture<T> future = null;
	private Object payload;
	private final ResultEventHandler<T> eventHandler;
	private final T accumulator;
	private Object transaction;
	
	public Request() {
		this.eventHandler = null;
		this.accumulator = null;
	}
	
	public Request(ResultEventHandler<T> eventHandler, T accumulator) {
		this.eventHandler = eventHandler;
		this.accumulator = accumulator;
	}
	
	public abstract void execute() throws Exception;
	
	public boolean cancel(boolean mayInterruptIfRunning) {
		return true;
	}
	
	public DefaultDbSessionFuture<T> getFuture() {
		return future;
	}
	
	public boolean canRemove() {
		return true;
	}

	public void setFuture(DefaultDbSessionFuture<T> future) {
		if (this.future != null) {
			throw new IllegalStateException("future can only be set once");
		}
		this.future = future;
	}

	public Object getPayload() {
		return payload;
	}

	public void setPayload(Object payload) {
		this.payload = payload;
	}

	public T getAccumulator() {
		return accumulator;
	}
	
	public ResultEventHandler<T> getEventHandler() {
		return eventHandler;
	}
	
	public Object getTransaction() {
		return transaction;
	}

	public void setTransaction(Object transaction) {
		this.transaction = transaction;
	}
	
}
