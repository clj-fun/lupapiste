jQuery(document).ready(function() {
  "use strict";

  var components = [{name: "server-settings"},
                    {name: "suti-api"},
                    {name: "municipality-maps"},
                    {name: "municipality-maps-layers"},
                    {name: "municipality-maps-map"}];

  ko.registerLupapisteComponents(components);
});
