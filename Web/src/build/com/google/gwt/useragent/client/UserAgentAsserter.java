/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.useragent.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

/**
 * Helper class, which, during startup, asserts that the specified user.agent
 * selection property value indeed matches the expected value for this browser /
 * user agent, thus avoid long hours debugging strange error messages when a
 * single user agent compile, typically created for testing purposes, ends up
 * being executed in the wrong browser.
 */
public class UserAgentAsserter implements EntryPoint {

	/**
	 * Replacement for UserAgentAsserter to disable it.
	 */
	public static class UserAgentAsserterDisabled implements EntryPoint {
		@Override
		public void onModuleLoad() { /* Empty - no assertions */}
	}

	@Override
	public void onModuleLoad() {
		UserAgent impl = GWT.create(UserAgent.class);//Dummy
	}

}
