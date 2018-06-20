require.config({
    baseUrl: "js",
    waitSeconds: 60,
    shim: {
    }
});


var modules = ['ngRoute',
             'graphdb.workbench.se.controllers',
               'graphdb.framework.core',
               'graphdb.framework.repositories',
               'graphdb.framework.impex.export',
               'graphdb.framework.impex.import',
               'graphdb.framework.security',
               'graphdb.framework.explore',
               'graphdb.framework.sparql',
               'graphdb.framework.graphexplore',
               'graphdb.framework.namespaces',
               'graphdb.framework.stats',
               'graphdb.framework.jmx.resources',
               'graphdb.framework.jmx.queries',
               'graphdb.framework.externalsync',
               'graphdb.framework.autocomplete',
               'graphdb.framework.ontorefine',
               'angularCancelOnNavigateModule'];


define([//'../../webjars/angularjs/1.3.8/angular',
                                  'angular/core/services',
                                  'angular/repositories/app',
                                  'angular/export/app',
                                  'angular/import/app',
                                  'angular/security/app',
                                  'angular/sparql/app',
                                  'angular/graphexplore/app',
                                  'angular/namespaces/app',
                                  'angular/explore/app',
                                  'angular/stats/app',
                                  'angular/resources/app',
                                  'angular/queries/app',
                                  'angular/externalsync/app',
                                  'angular/controllers',
                                  'angular/autocomplete/app',
                                  'angular/ontorefine/app',
                                  'angular/angularCancelOnNavigateModule'],
    function() {
		(function(angular){
		    var moduleDefinition = function(productInfo) {
               var workbench = angular.module('graphdb.workbench',
                       modules);

               workbench.config(['$routeProvider', '$locationProvider', '$menuItemsProvider', 'toastrConfig', 'localStorageServiceProvider',
                                 function($routeProvider, $locationProvider, $menuItemsProvider, toastrConfig, localStorageServiceProvider) {

                   angular.extend(toastrConfig, {
                       timeOut: 5000,
                       positionClass: 'toast-bottom-right'
                   });


                   localStorageServiceProvider
                       .setStorageType('localStorage')
                       .setNotify(true, true);

                   $routeProvider.when('/', {
                       templateUrl : 'v/8.2.0/pages/home.html',
                       controller : 'homeCtrl'
                   }).otherwise({
                       templateUrl : 'v/8.2.0/pages/not_found.html'
                   });

                   // use the HTML5 History API
                   $locationProvider.html5Mode(true);
               }]);

               workbench.constant('isEnterprise', productInfo.productType === "enterprise");
               workbench.constant('isFreeEdition', productInfo.productType === "free");
               workbench.constant('productInfo', productInfo);

               // we need to inject $jwtAuth here in order to init the service before everything else
               workbench.run(['$cookies', '$rootScope', '$route', 'toastr', '$jwtAuth', function($cookies, $rootScope, $route, toastr, $jwtAuth){
                   $rootScope.$on('$routeChangeSuccess', function() {
                       if ($route.current.title) {
                           document.title = $route.current.title + ' | GraphDB Workbench';
                       } else {
                           document.title = 'GraphDB Workbench';
                       }
                       $rootScope.helpInfo = $route.current.helpInfo;
                       $rootScope.title = $route.current.title;

                       toastr.clear();
                   });
               }]);

               angular.bootstrap(document, ['graphdb.workbench']);
       }

      $.get("rest/info/version?local=1", function( data ) {
          if (data.productType === "enterprise") {
            modules.push('graphdb.framework.clustermanagement');
            require(["angular/clustermanagement/app"], function() {moduleDefinition(data)});
          } else {
            moduleDefinition(data);
          }
      });



		})(angular)
	});