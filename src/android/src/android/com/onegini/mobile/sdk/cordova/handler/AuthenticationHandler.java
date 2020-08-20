/*
 * Copyright (c) 2017-2018 Onegini B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.onegini.mobile.sdk.cordova.handler;

import static com.onegini.mobile.sdk.cordova.OneginiCordovaPluginConstants.EVENT_SUCCESS;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import com.onegini.mobile.sdk.android.handlers.OneginiAuthenticationHandler;
import com.onegini.mobile.sdk.android.handlers.error.OneginiAuthenticationError;
import com.onegini.mobile.sdk.android.model.entity.CustomInfo;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;
import com.onegini.mobile.sdk.cordova.util.PluginResultBuilder;

public class AuthenticationHandler implements OneginiAuthenticationHandler {
  private CallbackContext callbackContext;

  public AuthenticationHandler(final CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

  @Override
  public void onSuccess(final UserProfile userProfile, final CustomInfo customInfo) {
    final PluginResult pluginResult = new PluginResultBuilder()
        .withSuccess()
        .withProfileId(userProfile)
        .withCustomInfo(customInfo)
        .withEvent(EVENT_SUCCESS)
        .build();

    sendPluginResult(pluginResult);
  }

  @Override
  public void onError(final OneginiAuthenticationError oneginiAuthenticationError) {
    oneginiAuthenticationError.printStackTrace();
    final PluginResultBuilder pluginResultBuilder = new PluginResultBuilder()
        .withError()
        .withOneginiError(oneginiAuthenticationError);

    if (oneginiAuthenticationError.getErrorType() == OneginiAuthenticationError.USER_DEREGISTERED) {
      pluginResultBuilder.withRemainingFailureCount(0);
    }

    sendPluginResult(pluginResultBuilder.build());
  }

  public void setCallbackContext(final CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

  private void sendPluginResult(final PluginResult pluginResult) {
    if (!callbackContext.isFinished()) {
      callbackContext.sendPluginResult(pluginResult);
    }
  }
}
