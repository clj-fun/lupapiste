/**
 * Base model for selecting application location
 */
LUPAPISTE.LocationModelBase = function(mapOptions) {
  "use strict";
  var self = this;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.x = 0;
  self.y = 0;
  self.address = ko.observable("");
  self.propertyId = ko.observable("");
  self.municipalityCode = ko.observable("");

  self.toJS = function() {
    return {
      x: self.x, y: self.y,
      address: self.address(),
      propertyId: util.prop.toDbFormat(self.propertyId())
    };
  };

  self.municipalityName = ko.pureComputed(function() {
    return self.municipalityCode() ? loc(["municipality", self.municipalityCode()]): "";
  });
  self.municipalitySupported = ko.observable(true);

  self.propertyIdHumanReadable = ko.pureComputed(function() {
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
  });

  self.propertyIdOk = ko.pureComputed(function() {
    return !_.isBlank(self.propertyId()) && util.prop.isPropertyId(self.propertyId());
  });
  self.addressOk = ko.pureComputed(function() { return self.municipalityCode() && !_.isBlank(self.address()); });

  self.propertyIdValidated = ko.observable(true);


  self.setAddress = function(a) {
    var newAddress = "";
    if (a) {
      newAddress = a.street;
      if (a.number && a.number !== "0") {
        newAddress = newAddress + " " + a.number;
      }
    }
    return self.municipalityCode(a ? a.municipality : "").address(newAddress);
  };

  //
  // Map and coordinate handling
  //

  self._map = null;

  self.map = function() {
    if (!self._map) {
      self._map = gis
        .makeMap(mapOptions.mapId, mapOptions.zoomWheelEnabled)
        .center(404168, 7205000, mapOptions.initialZoom)
        .addClickHandler(mapOptions.clickHandler);

      if (mapOptions.popupContentModel) {
        self._map.setPopupContentModel(self, mapOptions.popupContentModel);
      }
    }
    return self._map;
  };

  self.clearMap = function() {
    self.map().clear().updateSize();
    return self;
  };

  self.hasXY = function() {
    return self.x !== 0 && self.y !== 0;
  };

  self.drawLocation = function() {
    if (self.hasXY()) {
      self.map().clear().add({x: self.x, y: self.y}, true);
    } else {
      self.clearMap();
    }
    return self;
  };

  self.setXY = function(x, y) {
    self.x = x;
    self.y = y;
    return self.drawLocation();
  };

  self.center = function(zoom, x, y) {
    self.map().center(x || self.x, y || self.y, zoom);
    return self;
  };

  //
  // Concurrency control
  //

  self.requestContext = new RequestContext();
  self.beginUpdateRequest = function() {
    self.requestContext.begin();
    return self;
  };

  //
  // Search
  //

  self.onError = function() {
    hub.send("indicator", {style: "negative", message: "integration.getAddressNotWorking"});
  };

  self.searchPropertyId = function(x, y) {
    locationSearch.propertyIdByPoint(self.requestContext, x, y, function(id) {
        self.propertyId(id);
        self.propertyIdValidated(true);
      }, self.onError, self.processing);
    return self;
  };

  self.searchAddress = function(x, y) {
    locationSearch.addressByPoint(self.requestContext, x, y, self.setAddress, self.onError, self.processing);
    return self;
  };

};
