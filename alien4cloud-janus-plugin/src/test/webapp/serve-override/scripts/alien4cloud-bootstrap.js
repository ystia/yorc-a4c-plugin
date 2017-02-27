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
            require(['alien4cloud-Janus-plugin'], function() {
              alien4cloud.startup();
           });
          });
        });
	  });
   }
 };
});

