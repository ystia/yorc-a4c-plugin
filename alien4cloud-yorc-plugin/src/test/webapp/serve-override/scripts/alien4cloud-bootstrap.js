/*
 * Copyright 2018 Bull S.A.S. Atos Technologies - Bull, Rue Jean Jaures, B.P.68, 78340, Les Clayes-sous-Bois, France.
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
// override the default alien 4 cloud loading in order to automatically add the plugin so that grunt serve takes in account the local-files.
define(function (require) {
  'use strict';

  // require jquery and load plugins from the server
  var plugins = require('plugins');
  var alien4cloud = require('alien4cloud');
  var prefixer = require('scripts/plugin-url-prefixer');
  prefixer.enabled = false;

 var mods = {
   'nativeModules': require('a4c-native')
 };

 return {
   startup: function() {
      plugins.init().then(function() {
        require(mods.nativeModules , function() {
          require(['scripts/plugin-require.config.js'], function() {
            require(['alien4cloud-yorc-plugin'], function() {
              alien4cloud.startup();
           });
          });
        });
	  });
   }
 };
});

