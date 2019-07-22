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

'use strict';

const fs = require('fs');
const path = require('path');
const spawn = require('child_process').spawn;

const supportedPlatforms = ['android', 'ios'];
const envVariables = {
  enableAutoconfigure: 'ONEGINI_AUTOCONFIGURE',
  configuratorName: 'ONEGINI_SDK_CONFIGURATOR_PATH',
  configFiles: {
    android: 'ONEGINI_CONFIG_ANDROID_PATH',
    ios: 'ONEGINI_CONFIG_IOS_PATH'
  }
};
const extractedConfigPlugin = 'cordova-plugin-onegini-extracted-config';

module.exports = function (context) {
  if (process.env.ONEGINI_AUTOCONFIGURE === "false") {
    console.log('ONEGINI_AUTOCONFIGURE is set to false. Skipping Onegini SDK Configuration');
    return;
  }

  const projectRoot = context.opts.projectRoot;
  const hasExtractedConfig = hasExtractedConfigFiles(context);
  const deferral = context.requireCordovaModule('q').defer();
  const args = ['--cordova', '--app-dir', projectRoot];
  const platform = context.opts.plugin.platform;
  console.log(`Configuring the ${platform} platform`);
  console.log('===========================\n\n');

  if (supportedPlatforms.indexOf(platform) < -1) {
    console.log(`${platform} is not supported`);
    return deferral.promise;
  }
  
  let platformArgs = args.slice();
  platformArgs.unshift(platform);
  const configFile = getConfigFileForPlatform(projectRoot, platform, hasExtractedConfig);
  platformArgs.push('--config', configFile);

  execConfigurator(projectRoot, platform, hasExtractedConfig, platformArgs, deferral)
  return deferral.promise
};

function executeCommand(command, args) {
  return new Promise(function (resolve, reject) {
    console.log('\nRunning command:');
    console.log(`${command} ${args.join(' ')}\n`);

    const configurator = spawn(command, args);

    configurator.stdout.on('data', (data) => {
      process.stdout.write(data);
    });

    configurator.stderr.on('data', (data) => {
      process.stdout.write(data);
    });

    configurator.on('close', (code) => {
      if (code === 0) {
        resolve("finished");
      }
      else {
        reject("failed");
      }
    });
  });
}

function execConfigurator(projectRoot, platform, hasExtractedConfig, args, deferral) {
  const configuratorName = getConfiguratorName(projectRoot, platform, hasExtractedConfig);
  executeCommand(configuratorName, args)
    .then(copyArtifactoryCredentials(projectRoot, platform, hasExtractedConfig))
    .then(function () {
      deferral.resolve();
    }, function () {
      deferral.reject('Could not configure the Onegini SDK with your configuration');
    });
}

function getConfigFileForPlatform(projectRoot, platform, hasExtractedConfig) {
  const environmentVar = envVariables.configFiles[platform];
  const environmentLocation = process.env[environmentVar];
  const extractedConfigPluginLocation = `${projectRoot}/plugins/${extractedConfigPlugin}/onegini-config-${platform}.zip`;
  const defaultLocation = `${projectRoot}/onegini-config-${platform}.zip`;

  if (environmentLocation) {
    console.log(`Using Token Server config zip: '${environmentLocation}' set in '${environmentVar}'`);
    return environmentLocation;
  }

  if (hasExtractedConfig) {
    console.log(`Using Token Server config zip from '${extractedConfigPluginLocation}'`);
    return extractedConfigPluginLocation;
  }

  console.log(`Using default Token Server config zip: '${defaultLocation}'`);
  return defaultLocation;
}

function getConfiguratorName(projectRoot, platform, hasExtractedConfig) {
  const environmentVar = envVariables.configuratorName;
  const environmentName = process.env[environmentVar];
  const extractedName = `${projectRoot}/plugins/${extractedConfigPlugin}/onegini-sdk-configurator-${platform}`;
  const defaultName = 'onegini-sdk-configurator';

  if (environmentName) {
    console.log(`Using SDK Configurator executable in '${environmentName}' set in '${environmentVar}'`);
    return environmentName;
  }

  if (hasExtractedConfig) {
    console.log(`Using SDK Configurator executable in '${extractedName}'`);
    return extractedName;
  }

  console.log('Using SDK Configurator from $PATH');
  return defaultName;
}

function copyArtifactoryCredentials(projectRoot, platform, hasExtractedConfig) {
  return new Promise(function (resolve, reject) {
    if (platform === 'android' && hasExtractedConfig) {
      const filePath = `${projectRoot}/plugins/${extractedConfigPlugin}/artifactory.properties`;
      const destinationFilePath = path.join(projectRoot, 'platforms/android/artifactory.properties');
      const destDir = path.dirname(destinationFilePath);

      if (fs.existsSync(filePath) && fs.existsSync(destDir)) {
        console.log(`Copying '${filePath}' into '${destDir}'`);
        const stream = fs.createReadStream(filePath).pipe(fs.createWriteStream(destinationFilePath));
        stream.on('end', () => resolve("finished"));
        stream.on('error', (error) => reject("failed"));
        return;
      }
    }
    resolve("Skipping artifactory config");
  });
}

function hasExtractedConfigFiles(context) {
  return context.opts.cordova.plugins.indexOf(extractedConfigPlugin) > -1;
}
