/*
 * Copyright (c) 2016 Onegini B.V.
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

package com.onegini;

import static com.onegini.OneginiCordovaPluginConstants.ERROR_CODE_NO_SUCH_AUTHENTICATOR;
import static com.onegini.OneginiCordovaPluginConstants.ERROR_CODE_NO_USER_AUTHENTICATED;
import static com.onegini.OneginiCordovaPluginConstants.ERROR_CODE_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS;
import static com.onegini.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_NO_SUCH_AUTHENTICATOR;
import static com.onegini.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_NO_USER_AUTHENTICATED;
import static com.onegini.OneginiCordovaPluginConstants.ERROR_DESCRIPTION_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS;

import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import com.onegini.handler.AuthenticatorDeregistrationHandler;
import com.onegini.handler.AuthenticatorRegistrationHandler;
import com.onegini.handler.PinAuthenticationRequestHandler;
import com.onegini.mobile.sdk.android.handlers.request.callback.OneginiPinCallback;
import com.onegini.mobile.sdk.android.model.OneginiAuthenticator;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;
import com.onegini.util.ActionArgumentsUtil;
import com.onegini.util.PluginResultBuilder;

public class OneginiAuthenticatorRegistrationClient extends CordovaPlugin {
  private static final String ACTION_START = "start";
  private static final String ACTION_PROVIDE_PIN = "providePin";
  private static final String ACTION_DEREGISTER = "deregister";

  private AuthenticatorRegistrationHandler authenticatorRegistrationHandler;

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (ACTION_START.equals(action)) {
      startRegistration(args, callbackContext);
      return true;
    } else if (ACTION_PROVIDE_PIN.equals(action)) {
      providePin(args, callbackContext);
      return true;
    } else if (ACTION_DEREGISTER.equals(action)) {
      deregister(args, callbackContext);
      return true;
    }
    return false;
  }

  private void startRegistration(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final UserProfile userProfile = getOneginiClient().getUserClient().getAuthenticatedUserProfile();
    final Set<OneginiAuthenticator> authenticatorSet;
    final OneginiAuthenticator authenticator;

    if (userProfile == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_NO_USER_AUTHENTICATED, ERROR_CODE_NO_USER_AUTHENTICATED)
          .build());

      return;
    }

    authenticatorSet = getOneginiClient().getUserClient().getNotRegisteredAuthenticators(userProfile);
    authenticator = ActionArgumentsUtil.getAuthenticatorFromArguments(args, authenticatorSet);

    if (authenticator == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_NO_SUCH_AUTHENTICATOR, ERROR_CODE_NO_SUCH_AUTHENTICATOR)
          .build());

      return;
    }

    PinAuthenticationRequestHandler.getInstance().setStartAuthenticationCallbackContext(callbackContext);
    authenticatorRegistrationHandler = new AuthenticatorRegistrationHandler(callbackContext);

    cordova.getThreadPool().execute(new Runnable() {
      @Override
      public void run() {
        getOneginiClient().getUserClient().registerAuthenticator(authenticator, authenticatorRegistrationHandler);
      }
    });
  }

  private void providePin(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final String pin = ActionArgumentsUtil.getPinFromArguments(args);
    final OneginiPinCallback pinCallback = PinAuthenticationRequestHandler.getInstance().getPinCallback();
    authenticatorRegistrationHandler.setCallbackContext(callbackContext);

    if (pinCallback == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS, ERROR_CODE_PROVIDE_PIN_NO_AUTHENTICATION_IN_PROGRESS)
          .build());

      return;
    }

    pinCallback.acceptAuthenticationRequest(pin.toCharArray());
  }

  private void deregister(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    final UserProfile userProfile = getOneginiClient().getUserClient().getAuthenticatedUserProfile();
    final Set<OneginiAuthenticator> authenticatorSet;
    final OneginiAuthenticator authenticator;

    if (userProfile == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_NO_USER_AUTHENTICATED, ERROR_CODE_NO_USER_AUTHENTICATED)
          .build());

      return;
    }

    authenticatorSet = getOneginiClient().getUserClient().getRegisteredAuthenticators(userProfile);
    authenticator = ActionArgumentsUtil.getAuthenticatorFromArguments(args, authenticatorSet);

    if (authenticator == null) {
      callbackContext.sendPluginResult(new PluginResultBuilder()
          .withPluginError(ERROR_DESCRIPTION_NO_SUCH_AUTHENTICATOR, ERROR_CODE_NO_SUCH_AUTHENTICATOR)
          .build());

      return;
    }

    AuthenticatorDeregistrationHandler authenticationDeregistrationHandler = new AuthenticatorDeregistrationHandler(callbackContext);
    getOneginiClient().getUserClient().deregisterAuthenticator(authenticator, authenticationDeregistrationHandler);
  }

  private com.onegini.mobile.sdk.android.client.OneginiClient getOneginiClient() {
    return OneginiSDK.getInstance().getOneginiClient(cordova.getActivity().getApplicationContext());
  }
}
