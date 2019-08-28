/**
 * Controller for the resumable upload POC
 */
(function() {
    "use strict";

    angular
        .module("theHiveControllers")
        .controller("ResumableCtrl", function($rootScope, $scope, UtilsSrv) {
            $scope.flowInitOptions = {
                target: '/api/upload-chunks',
                generateUniqueIdentifier: UtilsSrv.uuidv4,
                chunkSize: 1024,
                simultaneousUploads: 1
            };
        });
})();
