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

import static com.onegini.OneginiCordovaPluginConstants.EXTRA_MOBILE_AUTHENTICATION;

import java.util.LinkedList;
import java.util.List;
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
import android.os.Bundle;
import com.onegini.handler.MobileAuthenticationHandler;
import com.onegini.mobile.sdk.android.handlers.OneginiInitializationHandler;
import com.onegini.mobile.sdk.android.handlers.error.OneginiInitializationError;
import com.onegini.mobile.sdk.android.model.OneginiClientConfigModel;
import com.onegini.mobile.sdk.android.model.entity.UserProfile;
import com.onegini.util.PluginResultBuilder;

public class OneginiClient extends CordovaPlugin {

  private static final String ACTION_START = "start";
  private final List<Bundle> delayedMobileAuthenticationRequests = new LinkedList<Bundle>();

  @Override
  public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
    super.initialize(cordova, webView);
    final Bundle mobileAuthenticationBundle = cordova.getActivity().getIntent().getBundleExtra(EXTRA_MOBILE_AUTHENTICATION);
    handleMobileAuthenticationRequest(mobileAuthenticationBundle);
  }

  @Override
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (ACTION_START.equals(action)) {
      start(callbackContext);
      return true;
    }

    return false;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    final Bundle mobileAuthenticationPushMessage = intent.getBundleExtra(EXTRA_MOBILE_AUTHENTICATION);

    handleMobileAuthenticationRequest(mobileAuthenticationPushMessage);
    handleRedirection(intent.getData());
  }

  private void handleMobileAuthenticationRequest(final Bundle pushMessage) {
    if (pushMessage == null) {
      return;
    }

    if (OneginiSDK.getInstance().isStarted()) {
      OneginiSDK.getInstance().getOneginiClient(getApplicationContext()).getUserClient()
          .handleMobileAuthenticationRequest(pushMessage, MobileAuthenticationHandler.getInstance());
    } else {
      delayedMobileAuthenticationRequests.add(pushMessage);
    }
  }

  private void handleDelayedMobileAuthenticationRequests() {
    for (Bundle bundle : delayedMobileAuthenticationRequests) {
      OneginiSDK.getInstance().getOneginiClient(getApplicationContext()).getUserClient()
          .handleMobileAuthenticationRequest(bundle, MobileAuthenticationHandler.getInstance());
      delayedMobileAuthenticationRequests.remove(bundle);
    }
  }

  private void handleRedirection(final Uri uri) {
    final com.onegini.mobile.sdk.android.client.OneginiClient client = OneginiSDK.getInstance().getOneginiClient(getApplicationContext());
    if (uri != null && client.getConfigModel().getRedirectUri().startsWith(uri.getScheme())) {
      client.getUserClient().handleRegistrationCallback(uri);
    }
  }

  private void start(final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        OneginiSDK.getInstance().startSDK(getApplicationContext(), new OneginiInitializationHandler() {
          @Override
          public void onSuccess(final Set<UserProfile> set) {
            handleDelayedMobileAuthenticationRequests();
            sendOneginiClientStartSuccessResult(callbackContext);
          }

          @Override
          public void onError(final OneginiInitializationError initializationError) {
            sendOneginiClientStartErrorResult(callbackContext, initializationError);
          }
        });
      }
    });
  }

  private void sendOneginiClientStartSuccessResult(final CallbackContext callbackContext) {
    final OneginiClientConfigModel configModel = OneginiSDK.getInstance().getOneginiClient(getApplicationContext()).getConfigModel();

    final PluginResult pluginResult = new PluginResultBuilder()
        .withSuccess()
        .withOneginiConfigModel(configModel)
        .build();

    callbackContext.sendPluginResult(pluginResult);
  }

  private void sendOneginiClientStartErrorResult(final CallbackContext callbackContext, final OneginiInitializationError initializationError) {
    final PluginResult pluginResult = new PluginResultBuilder()
        .withError()
        .withOneginiError(initializationError)
        .build();

    callbackContext.sendPluginResult(pluginResult);
  }

  private Context getApplicationContext() {
    return cordova.getActivity().getApplicationContext();
  }
}