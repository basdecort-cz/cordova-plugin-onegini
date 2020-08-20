/*
 * Copyright (c) 2017-2019 Onegini B.V.
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

package com.onegini.mobile.sdk.cordova.client;

import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.onegini.mobile.sdk.android.handlers.OneginiInitializationHandler;
import com.onegini.mobile.sdk.android.handlers.OneginiResetHandler;
import com.onegini.mobile.sdk.android.handlers.error.OneginiError;
import com.onegini.mobile.sdk.android.handlers.error.OneginiInitializationError;
import com.onegini.mobile.sdk.android.handlers.error.OneginiResetError;
import com.onegini.mobile.sdk.android.model.OneginiClientConfigModel;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;
import com.onegini.mobile.sdk.cordova.OneginiSDK;
import com.onegini.mobile.sdk.cordova.handler.BrowserRegistrationRequestHandler;
import com.onegini.mobile.sdk.cordova.util.PluginResultBuilder;
import com.onegini.mobile.sdk.cordova.util.PreferencesUtil;

@SuppressWarnings("unused")
public class OneginiClient extends CordovaPlugin {

  private static final String ACTION_START = "start";
  private static final String ACTION_RESET = "reset";
  private PreferencesUtil preferencesUtil;

  @Override
  public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
    super.initialize(cordova, webView);
    preferencesUtil = new PreferencesUtil(preferences);

    /*
      Prepare an instance of the SDK.
      Used to avoid the performance drag of building the SDK has when calling the first instance.
      Especially when classes from a secondary dex file are required to start the SDK, this can be a significant performance improvement.
     */
    getOneginiClient();
  }

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (ACTION_START.equals(action)) {
      start(callbackContext);
      return true;
    } else if (ACTION_RESET.equals(action)) {
      reset(callbackContext);
      return true;
    }

    return false;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleRedirection(intent.getData());
  }

  private void handleRedirection(final Uri uri) {
    if (uri == null) {
      return;
    }

    final com.onegini.mobile.sdk.android.client.OneginiClient client = getOneginiClient();
    if (shouldOpenBrowserForRegistration() && client.getConfigModel().getRedirectUri().startsWith(uri.getScheme())) {
      BrowserRegistrationRequestHandler.handleRegistrationCallback(uri);
    }
  }

  private void start(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        OneginiSDK.getInstance().startSDK(getApplicationContext(), new OneginiInitializationHandler() {
          @Override
          public void onSuccess(final Set<UserProfile> set) {
            sendOneginiClientSuccessResult(callbackContext);
          }

          @Override
          public void onError(final OneginiInitializationError initializationError) {
            initializationError.printStackTrace();
            sendOneginiClientErrorResult(callbackContext, initializationError);
          }
        });
      }
    });
  }

  private void reset(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        OneginiSDK.getInstance().getOneginiClient(getApplicationContext()).reset(new OneginiResetHandler() {

          @Override
          public void onSuccess(final Set<UserProfile> removedUserProfiles) {
            sendOneginiClientSuccessResult(callbackContext);
          }

          @Override
          public void onError(final OneginiResetError error) {
            sendOneginiClientErrorResult(callbackContext, error);
          }
        });
      }
    });
  }

  private void sendOneginiClientSuccessResult(final CallbackContext callbackContext) {
    final OneginiClientConfigModel configModel = getOneginiClient().getConfigModel();

    final PluginResult pluginResult = new PluginResultBuilder()
        .withSuccess()
        .withOneginiConfigModel(configModel)
        .build();

    callbackContext.sendPluginResult(pluginResult);
  }

  private void sendOneginiClientErrorResult(final CallbackContext callbackContext, final OneginiError oneginiError) {
    final PluginResult pluginResult = new PluginResultBuilder()
        .withError()
        .withOneginiError(oneginiError)
        .build();

    callbackContext.sendPluginResult(pluginResult);
  }

  private Context getApplicationContext() {
    return cordova.getActivity().getApplicationContext();
  }

  private com.onegini.mobile.sdk.android.client.OneginiClient getOneginiClient() {
    return OneginiSDK.getInstance().getOneginiClient(getApplicationContext());
  }

  private boolean shouldOpenBrowserForRegistration() {
    return !preferencesUtil.isWebViewDisabled();
  }
}
