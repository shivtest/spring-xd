/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Definition of the Module Details controller
 *
 * @author Gunnar Hillert
 */
define([], function () {
  'use strict';
  return ['$scope', 'JobModuleService', 'XDCommon', '$state', '$stateParams',
    function ($scope, jobModuleService, xdCommon, $state, $stateParams) {
      $scope.$apply(function () {
        $scope.moduleName = $stateParams.moduleName;
        $scope.optionsPredicate = 'name';

        var singleModulePromise = jobModuleService.getSingleModule($stateParams.moduleName).$promise;
        xdCommon.addBusyPromise(singleModulePromise);

        singleModulePromise.then(
            function (result) {
                $scope.moduleDetails = result;
              }, function (error) {
                xdCommon.$log.error('Error fetching data. Is the XD server running?');
                xdCommon.$log.error(error);
                xdCommon.growl.addErrorMessage('Error fetching data. Is the XD server running?');
              }
            );

        var moduleDefinitionPromise = jobModuleService.getModuleDefinition($stateParams.moduleName);
        xdCommon.addBusyPromise(moduleDefinitionPromise);
        moduleDefinitionPromise.success(function(data){
          $scope.moduleDefinition = data;
        })
        .error(function(error){
          xdCommon.$log.error('Error fetching data. Is the XD server running?');
          xdCommon.$log.error(error);
          xdCommon.growl.addErrorMessage('Error fetching data. Is the XD server running?');
        });
        $scope.closeModuleDetails = function () {
            xdCommon.$log.info('Closing Job Details Window');
            $state.go('home.jobs.tabs.modules');
          };
      });
    }];
});
