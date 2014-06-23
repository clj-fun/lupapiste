var gis = (function() {
  "use strict";


  var iconDefaultPath  = "/img/map-marker.png";
  var iconLocMapping = {
    "sameLocation"  : iconDefaultPath,
    "sameOperation" : "/img/map-marker-red.png",
    "others"        : "/img/map-marker-green.png",
    "cluster"       : "/img/map-marker-group.png"
  };

  // Map initialization

  function Map(element, zoomWheelEnabled) {
    var self = this;

    if (features.enabled("use-wmts-map")) {

      self.map = new OpenLayers.Map(element, {
        theme: "/theme/default/style.css",
        projection: new OpenLayers.Projection("EPSG:3067"),
        units: "m",
        maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
        resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
        controls: [ new OpenLayers.Control.Zoom(),
                    new OpenLayers.Control.Navigation({ zoomWheelEnabled: zoomWheelEnabled }) ]
      });
      OpenLayers.ImgPath = '/theme/default/img/';

    } else {

      self.map = new OpenLayers.Map(element, {
        theme: "/theme/default/style.css",
        projection: new OpenLayers.Projection("EPSG:3067"),
        units: "m",
        maxExtent: new OpenLayers.Bounds(0,0,10000000,10000000),
        resolutions : [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25],
        controls: [ new OpenLayers.Control.Zoom(),
                    new OpenLayers.Control.Navigation({ zoomWheelEnabled: zoomWheelEnabled }) ]
      });

    }


    // Layers

    // use the old proxy server to wms/wmts
    var mapServer = features.enabled("use-wmts-map") ? LUPAPISTE.config.maps["proxyserver-wmts"] : LUPAPISTE.config.maps["proxyserver-wms"];
    if (mapServer.indexOf(",") > -1) {
      mapServer = mapServer.split(",");
    }
    var base = new OpenLayers.Layer("", {displayInLayerSwitcher: false, isBaseLayer: true});

    if (features.enabled("use-wmts-map")) {   // Uusi: WMTS-layerit

      var taustakartta = new OpenLayers.Layer.WMTS({
        name: "Taustakartta",
        url: mapServer,
        isBaseLayer: false,
        requestEncoding: "KVP",
        layer: "taustakartta",
        matrixSet: "ETRS-TM35FIN",
        format: "image/png",
        style: "default",
        opacity: 1.0,
        resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
        // maxExtent not defined here -> inherits from the config of the map
        projection: new OpenLayers.Projection("EPSG:3067")
      });
      var kiinteistorajat = new OpenLayers.Layer.WMTS({
        name: "Kiinteistojaotus",
        url: mapServer,
        isBaseLayer: false,
        requestEncoding: "KVP",
        layer: "kiinteistojaotus",
        matrixSet: "ETRS-TM35FIN",
        format: "image/png",
        style: "default",
        opacity: 1.0,
        resolutions: [4, 2, 1, 0.5],
        // maxExtent not defined here -> inherits from the config of the map
        projection: new OpenLayers.Projection("EPSG:3067")
      });
      var kiinteistotunnukset = new OpenLayers.Layer.WMTS({
        name: "Kiinteistotunnukset",
        url: mapServer,
        isBaseLayer: false,
        requestEncoding: "KVP",
        layer: "kiinteistotunnukset",
        matrixSet: "ETRS-TM35FIN",
        format: "image/png",
        style: "default",
        opacity: 1.0,
        resolutions: [4, 2, 1, 0.5],
        // maxExtent not defined here -> inherits from the config of the map
        projection: new OpenLayers.Projection("EPSG:3067")
      });

    } else {  // Vanha: WMS-layerit

      var taustakartta = new OpenLayers.Layer.WMS(
          "taustakartta",
          mapServer,
          {layers: "taustakartta", format: "image/png"},
          {isBaseLayer: false});
      var kiinteistorajat = new OpenLayers.Layer.WMS(
          "kiinteistorajat",
          mapServer,
          {layers: "ktj_kiinteistorajat", format: "image/png", transparent: true},
          {isBaseLayer: false, maxScale: 1, minScale: 20000}
          );
      var kiinteistotunnukset = new OpenLayers.Layer.WMS(
          "kiinteistotunnukset",
          mapServer,
          {layers: "ktj_kiinteistotunnukset", format: "image/png", transparent: true},
          {isBaseLayer: false, maxScale: 1, minScale: 10000});

    }


    self.vectorLayer = new OpenLayers.Layer.Vector("Vector layer");

    if (!features.enabled("maps-disabled")) {
      self.map.addLayers([base, taustakartta, kiinteistorajat, kiinteistotunnukset, self.vectorLayer]);
    } else {
      self.map.addLayers([base, self.vectorLayer]);
    }


    if (features.enabled("use-wmts-map")) {

      //
      // Hack: Did not manage to adjust the configs of the layers and the map (resolutions and maxExtent)
      //       so that the old resolutions array [2000, 1000, 500, 200, 100, 50, 20, 10, 4, 2, 1, 0.5, 0.25]
      //       would work.
      //
      self.map.events.register('zoomend', self.map, function (event) {
        // hide marker contents div on the inforequest markers map, because marker clustering may have been divided or merged markers
        if (self.markerMapCloseCallback) {
          self.markerMapCloseCallback();
        }

        if( self.map.getZoom() < 2) {
          // For some reason, calling only "self.map.zoomTo(2);" did not work here.
          // http://gis.stackexchange.com/questions/25080/why-doesnt-openlayers-zoom
          self.map.setCenter(self.map.getCenter(), 2);
        }
      });
    }


    // Markers


    var getIconHeight = function(feature) {
      return (feature.cluster && (feature.cluster.length > 1 || feature.cluster[0].attributes.isCluster)) ? 32 : 25;
    };

    var context = {
      extGraphic: function(feature) {
        var iconPath = "img/map-marker.png";
        if (feature.cluster) {
          if (feature.cluster.length > 1) {
            iconPath = iconLocMapping["cluster"];
          } else {
            iconPath = feature.cluster[0].attributes.isCluster ? iconLocMapping["cluster"] : feature.cluster[0].style.externalGraphic;
          }
        } else {
          iconPath = feature.style.externalGraphic;
        }
        return iconPath || iconDefaultPath;
      },
      graphicWidth: function(feature) {
        return (feature.cluster && (feature.cluster.length > 1 || feature.cluster[0].attributes.isCluster)) ? 32 : 21;
      },
      graphicHeight: function(feature) {
        return getIconHeight(feature);
      },
      graphicYOffset: function(feature) {
        return -1 * getIconHeight(feature);
      }
    };

    var stylemap = new OpenLayers.StyleMap({
      'default': new OpenLayers.Style({
        externalGraphic: '${extGraphic}',
        graphicWidth:    '${graphicWidth}',
        graphicHeight:   '${graphicHeight}',   //alt to pointRadius
        graphicYOffset:  '${graphicYOffset}',
        cursor:          'default'
      }, {
        context: context
      }),
      'select': new OpenLayers.Style({
        // This 'select' cannot be completely removed from here, because then the markers' select functionality
        // starts to act in an unwanted way: i.e. previously selected markers first dim and then disappear
        // on the following selections etc.
//        cursor: 'pointer'
      })
    });

    var strategy = new OpenLayers.Strategy.Cluster({distance: 25/*, threshold: 2*/});

    self.markerLayer = new OpenLayers.Layer.Vector( "Markers" , {strategies: [strategy], styleMap: stylemap} );
    self.map.addLayer(self.markerLayer);

    self.markers = [];


    self.clear = function() {
      if (self.markerMapCloseCallback) {
        self.markerMapCloseCallback();
      }

      self.vectorLayer.removeAllFeatures();

      self.markerLayer.removeAllFeatures();
      _.each(self.markers, function(marker) { marker.destroy(); });
      self.markers = [];

      return self;
    };


    // Select control

    self.selectedFeature = null;
    self.closePopup = function(e) {
      self.selectControl.unselect(self.selectedFeature);
    };
    self.selectControl = new OpenLayers.Control.SelectFeature(self.markerLayer, {
      autoActivate: true,
      clickOut: true,
      toggle: true,

      onSelect: function(feature) {
        self.selectedFeature = feature;

        if (self.popupContentProvider) {
          var popupContentProviderResp = self.popupContentProvider(feature);
          var popupId = "popup-id";

          var popup = new OpenLayers.Popup.FramedCloud(
              popupId,                                              // id (not used)
              feature.geometry.getBounds().getCenterLonLat(),       // lonlat
              null,                                                 // contentSize
              popupContentProviderResp.html,                        // (html content)
              null,                                                 // anchor
              true,                                                 // closeBox
              self.closePopup);                                     // closeBoxCallback

          popup.panMapIfOutOfView = true;
          popup.autoSize = true;
          popup.minSize = new OpenLayers.Size(470, 220);
          popup.maxSize = new OpenLayers.Size(550, 550);
          popup.fixedRelativePosition = true;
          feature.popup = popup;
          self.map.addPopup(popup, true);

          popupContentProviderResp.applyBindingsFn(popupId);
        }

        if (self.markerClickCallback) {
          var contents = feature.cluster ?
                          _.reduce(
                            feature.cluster,
                            function(acc, entry) {
                              return acc + entry.data.contents;
                            },
                            "") :
                          feature.data.contents;
          self.markerClickCallback(contents);
        }
      },

      onUnselect: function(feature) {
        if (feature && feature.popup) {
          self.map.removePopup(feature.popup);
          feature.popup.destroy();
          feature.popup = null;
        }
        self.selectedFeature = null;

        if (self.markerMapCloseCallback) {
          self.markerMapCloseCallback();
        }
      }
    });

    self.map.addControl(self.selectControl);


    // Adding markers

    self.add = function(markerInfos) {
      var newMarkers = [];
      markerInfos = _.isArray(markerInfos) ? markerInfos : [markerInfos];

      _.each(markerInfos, function(markerInfo) {
        var iconName = markerInfo.isCluster ? "cluster" : markerInfo.iconName;
        var iconPath = iconLocMapping[iconName] || iconDefaultPath;
        var markerFeature = new OpenLayers.Feature.Vector(
            new OpenLayers.Geometry.Point(markerInfo.x, markerInfo.y),
            {isCluster: markerInfo.isCluster || false,
             contents: markerInfo.contents || "" },
            {externalGraphic: iconPath});
        self.markers.push(markerFeature);
        newMarkers.push(markerFeature);

      });  //each

      self.markerLayer.addFeatures(newMarkers);
      return self;
    };

    self.setPopupContentProvider = function(handler) {
      self.popupContentProvider = handler;
      return self;
    };

    self.setMarkerClickCallback = function(handler) {
      self.markerClickCallback = handler;
      return self;
    };

    self.setMarkerMapCloseCallback = function(handler) {
      self.markerMapCloseCallback = handler;
      return self;
    };

    // Map handling functions

    self.center = function(x, y, zoom) {
      self.map.setCenter(new OpenLayers.LonLat(x, y), zoom);
      return self;
    };

    self.zoomTo = function(zoom) {
      self.map.zoomTo(zoom);
      return self;
    };

    self.getZoom = function() {
      return self.map.getZoom();
    };

    self.getMaxZoom = function() {
      return 11;
    };

    self.centerWithMaxZoom = function(x, y) {
      return self.center(x, y, self.getMaxZoom());
    };

    self.updateSize = function() {
      self.map.updateSize();
      return self;
    };

    self.drawDrawings = function(drawings, attrs, style) {
      if (drawings) {
        var addFeatureFn = function(memo, drawing) {
          var newFeature = new OpenLayers.Feature.Vector(OpenLayers.Geometry.fromWKT(drawing.geometry), attrs, style);
          memo.push(newFeature);
          return memo;
        };
        var featureArray = _.reduce(drawings, addFeatureFn, []);
        if (featureArray.length > 0) self.vectorLayer.addFeatures(featureArray);
      }
      return self;
    };

    self.addClickHandler = function(handler) {
      var ClickControl = OpenLayers.Class(OpenLayers.Control, {
        defaultHandlerOptions: {
          "single": true,
          "double": false,
          "pixelTolerance": 0,
          "stopSingle": false,
          "stopDouble": false
        },

        initialize: function() {
          this.handlerOptions = OpenLayers.Util.extend({}, this.defaultHandlerOptions);
          OpenLayers.Control.prototype.initialize.apply(this, arguments);
          this.handler = new OpenLayers.Handler.Click(this, {"click": this.trigger}, this.handlerOptions);
        },

        trigger: function(e) {
          var event = getEvent(e);
          //
          // When marker (event.target.nodeName === "image") is clicked, let's prevent further reacting to the click here.
          // This is somewhat a hack. It would be better to find a way to somehow stop propagation of click event earlier
          // in the selectControl's onSelect callback, or by the marker item (OpenLayers.Feature.Vector) itself.
          //
          if (!event.target || event.target.nodeName !== "image") {
            var pos = self.map.getLonLatFromPixel(event.xy);
            handler(pos.lon, pos.lat);
          }
        }
      });

      var click = new ClickControl();
      self.map.addControl(click);
      click.activate();

      return self;
    };
  }

  return {
    makeMap: function(element, zoomWheelEnabled) { return new Map(element, zoomWheelEnabled); }
  };

})();
