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

module.exports = (function (XMLHttpRequest) {
  var utils = require('./utils'),
      HEADER_LENGTH = 4,
      resourceBaseUrl,
      nativeXhrProperties = [
        'onabort',
        'onerror',
        'onload',
        'onloadend',
        'onloadstart',
        'onprogress',
        'onreadystatechange',
        'ontimeout',
        'readyState',
        'response',
        'responseText',
        'responseType',
        'responseURL',
        'responseXML',
        'status',
        'statusText',
        'timeout',
        'upload',
        'XMLHttpRequestUpload',
        'withCredentials'
      ],
      nativeXhrMethods = [
        'abort',
        'getAllResponseHeaders',
        'getResponseHeader',
        'overrideMimeType',
        'send',
        'setRequestHeader'
      ];

  function fetch(options, successCb, failureCb) {
    var _successCb = successCb,
        _failureCb = failureCb;

    options = utils.getOptionsWithDefaults(options, {
      method: 'GET',
      headers: {},
      anonymous: false
    }, 'url');

    if (!options || !options.url) {
      throw new TypeError("Onegini: missing 'url' argument for fetch");
    }

    function getMetaLength(buffer) {
      var array = new Uint8Array(buffer, 0, HEADER_LENGTH);
      return ((array[array.length - 4]) |
      (array[array.length - 3] << 8) |
      (array[array.length - 2] << 16) |
      (array[array.length - 1] << 24));
    }

    function httpResponseFromArrayBuffer(buffer) {
      var metaLength = getMetaLength(buffer),
          metadata = new Uint8Array(buffer, HEADER_LENGTH, metaLength),
          result = JSON.parse(String.fromCharCode.apply(null, metadata)),
          rawBody;

      if (ArrayBuffer.prototype.slice) {
        rawBody = buffer.slice(HEADER_LENGTH + metaLength, buffer.byteLength)
      } else {
        rawBody = new ArrayBuffer(buffer.byteLength - metaLength - HEADER_LENGTH);
        var bodyArray = new Uint8Array(rawBody);
        var bufferArray = new Uint8Array(buffer);

        for (var i = 0; i < bodyArray.length; i++) {
          bodyArray[i] = bufferArray[i + HEADER_LENGTH + metaLength];
        }
      }

      Object.defineProperties(result, {
        'rawBody': {
          value: rawBody
        },
        'body': {
          get: function () {
            var bodyData = new Uint8Array(this.rawBody);
            return String.fromCharCode.apply(null, bodyData);
          }
        },
        'json': {
          get: function () {
            return JSON.parse(this.body);
          }
        }
      });

      return result;
    }

    function success(buffer) {
      _successCb(httpResponseFromArrayBuffer(buffer))
    }

    function failure(buffer) {
      _failureCb({
        code: 8013,
        description: 'Onegini: HTTP Request failed. Check httpResponse for more info.',
        httpResponse: httpResponseFromArrayBuffer(buffer)
      });
    }

    utils.callbackExec('OneginiResourceClient', 'fetch', options, success, failure);

    if (successCb) {
      return
    }

    return new Promise(function (resolve, reject) {
      _successCb = resolve;
      _failureCb = reject;
    });
  }

  function init(url) {
    window.XMLHttpRequest = OneginiXMLHttpRequest;
    resourceBaseUrl = url;
  }

  function disable() {
    window.XMLHttpRequest = XMLHttpRequest;
  }

  function OneginiXMLHttpRequest() {
    this._eventListeners = {};
    this.xhr = new XMLHttpRequest();
  }

  OneginiXMLHttpRequest.prototype.open = function (method, url) {
    if (url.substr(0, resourceBaseUrl.length) === resourceBaseUrl) {
      setupXhrProxy(this, method, url);
    }

    this.xhr.open.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.addEventListener = function (type, listener) {
    this._eventListeners[type] = this._eventListeners[type] || [];
    this._eventListeners[type].push(listener);

    this.xhr.addEventListener.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.removeEventListener = function (type, listener) {
    var listeners = this._eventListeners[type];

    for (var i = 0, l = listeners.length; i < l; i++) {
      if (listeners[i] === listener) {
        listeners.splice(i, 1);
        return this.removeEventListener(type, listener);
      }
    }

    this.xhr.removeEventListener.apply(this.xhr, arguments);
  };

  OneginiXMLHttpRequest.prototype.dispatchEvent = function (event) {
    var listeners = this._eventListeners[event.type];

    if (this['on' + event.type]) {
      this['on' + event.type].call(this);
    }

    if (listeners && listeners.length !== 0) {
      event.target = this;
      for (var i = 0, l = listeners.length; i < l; i++) {
        listeners[i].call(this, event);
      }
    }

    if (this.onreadystatechange) {
      this.onreadystatechange();
    }
  };

  nativeXhrProperties.forEach(function (property) {
    linkXhrPropertyWithOneginiXhr(property);
  });

  nativeXhrMethods.forEach(function (method) {
    linkXhrMethodWithOneginiXhr(method);
  });

  function linkXhrPropertyWithOneginiXhr(key) {
    Object.defineProperty(OneginiXMLHttpRequest.prototype, key, {
      get: function () {
        return this.xhr[key];
      },
      set: function (value) {
        return this.xhr[key] = value;
      }
    });
  }

  function linkXhrMethodWithOneginiXhr(name) {
    Object.defineProperty(OneginiXMLHttpRequest.prototype, name, {
      value: function () {
        return this.xhr[name].apply(this.xhr, arguments);
      }
    });
  }

  function setupXhrProxy(xhr, method, url) {
    xhr._requestHeaders = {};
    xhr._responseHeaders = {};
    xhr._method = method;
    xhr._url = url;

    defineProperty(xhr, 'send', function (body) {
      fetch({
        method: xhr._method,
        url: xhr._url,
        headers: xhr._requestHeaders,
        body: body
      }, function (successResponse) {
        populateXhrWithFetchResponse(xhr, successResponse);
        xhr.dispatchEvent(new Event('load'));
      }, function (err) {
        populateXhrWithFetchResponse(xhr, err.httpResponse);
        xhr.dispatchEvent(new Event('error'));
      });
    });

    defineProperty(xhr, 'getAllResponseHeaders', function () {
      var headersString = '';

      for (var header in xhr._responseHeaders) {
        headersString += header + ': ' + xhr._responseHeaders[header] + '\n';
      }

      return headersString;
    });

    defineProperty(xhr, 'getResponseHeader', function (header) {
      return xhr._responseHeaders[header];
    });

    defineProperty(xhr, 'setRequestHeader', function (header, value) {
      xhr._requestHeaders[header] = value;
    });
  }

  function populateXhrWithFetchResponse(xhr, result) {
    if (xhr.responseType === 'arrayBuffer') {
      defineProperty(xhr, 'response', result.rawBody);
    } else {
      defineProperty(xhr, 'response', result.body);
    }
    defineProperty(xhr, 'readyState', 4);
    defineProperty(xhr, 'responseText', result.body);
    defineProperty(xhr, 'status', result.status);
    defineProperty(xhr, 'statusText', result.statusText);
    xhr._responseHeaders = result.headers;
  }

  function defineProperty(object, property, value) {
    Object.defineProperty(object, property, {
      configurable: true,
      value: value
    });
  }

  return {
    fetch: fetch,
    init: init,
    disable: disable
  };

})(XMLHttpRequest);