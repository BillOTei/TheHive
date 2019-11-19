/**
 * Controller for the resumable upload POC
 */
/* jshint browser: true */
(function() {
    "use strict";

    angular
        .module("theHiveControllers")
        .controller("ResumableCtrl", function(
            $rootScope,
            $scope,
            UtilsSrv,
            $http
        ) {
            $scope.flowInitOptions = {
                target: "/api/upload-chunks",
                generateUniqueIdentifier: UtilsSrv.uuidv4,
                chunkSize: 2 * 1024 * 1024,
                simultaneousUploads: 1
            };

            $scope.download = function(file) {
                return $http({
                    method: "GET",
                    url: "./api/attachment/" + file.uniqueIdentifier,
                    responseType: "arraybuffer"
                })
                    .then(function(resp) {
                        var headersArr = resp.headers();

                        var filename = headersArr["x-filename"];
                        var contentType = headersArr["content-type"];

                        var linkElement = document.createElement("a");
                        try {
                            var blob = new Blob([resp.data], {
                                type: contentType
                            });
                            var url = window.URL.createObjectURL(blob);

                            linkElement.setAttribute("href", url);
                            linkElement.setAttribute("download", filename);

                            var clickEvent = new MouseEvent("click", {
                                view: window,
                                bubbles: true,
                                cancelable: false
                            });
                            linkElement.dispatchEvent(clickEvent);
                        } catch (ex) {
                            console.log(ex);
                        }
                    })
                    .catch(function(data) {
                        console.log(data);
                    });
            };
        });
})();
