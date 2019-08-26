/**
 * Controller for the resumable upload POC
 */
(function() {
    "use strict";

    angular
        .module("theHiveControllers")
        .controller("ResumableCtrl", function($rootScope, $scope) {
            $scope.fileAdded = function($file, $event, $flow) {
                console.log($file);
            };
        });
})();
