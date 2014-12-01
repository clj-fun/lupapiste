jQuery(function($) {
  "use strict";

  function applyFixture(fixture) {
    ajax.query("apply-fixture", {"name": fixture})
      .success(function() { $("#debug-apply-done").text(" DONE!").show().delay(1000).fadeOut(); })
      .error(function(e) {alert(e.text);})
      .call();
    return false;
  }

  function createApplication(operation) {
    var municipality = "753";
    if (currentUser.isAuthority()) {
      var org = currentUser.get().organizations()[0];
      municipality = org.split("-")[0];
    }
    $.ajax({
      url: "/dev/create",
      data: { address: "Latokuja 3",
              propertyId: municipality + "-416-55-7",
              municipality: municipality,
              operation: operation,
              x: "404369.304000",
              y: "6693806.957000",
              kuntalupatunnus: null },
      success: function() { $("#debug-create-done").text(" DONE!").show().delay(1000).fadeOut(); }
    });
    return false;
  }

  function throttle(type, e) {
    var t = $(e.target);
    var value = t.val();
    ajax.post(window.location.protocol + "//" + window.location.host + "/perfmon/throttle/" + type)
      .raw()
      .json({value: value})
      .header("npm", "true")
      .success(function() { t.parent().find("b.dev-throttle-" + type).text(value); })
      .call();
  }

  function loadTimingData() {
    if (!window.performance) { return; }

    if (!window.performance.timing.loadEventEnd) {
      setTimeout(loadTimingData, 10);
      return;
    }

    var table = $("footer table.dev-debug-timing");
    var data = [["fetch", "fetchStart", "requestStart"],
                ["req", "requestStart", "responseStart"],
                ["resp", "responseStart", "responseEnd"],
                ["network", "fetchStart", "responseEnd"],
                ["display", "responseEnd", "loadEventEnd"],
                ["total", "navigationStart", "loadEventEnd"]];

    _.each(data, function(row) {
      var name = row[0],
          start = window.performance.timing[row[1]],
          end = window.performance.timing[row[2]],
          duration = end - start;
      if (typeof start !== "number") {throw "Unknown timineg event: " + row[1]; }
      if (typeof end !== "number") {throw "Unknown timineg event: " + row[2]; }
      table
        .append($("<tr>").css("padding", "0px")
          .append($("<td>").text(name).css("padding", "0px"))
          .append($("<td>").text(duration).css("padding", "0px").css("text-align","right")));
    });

    ajax.post(window.location.protocol + "//" + window.location.host + "/perfmon/browser-timing")
      .raw()
      .json({timing: window.performance.timing})
      .header("npm", "true")
      .call();
  }

  $("footer")
    .append($("<div>").addClass("dev-debug")
      .append($("<h3>")
        .append($("<a>").attr("href", "#").text("Development").click(function() { $("footer .dev-debug div:eq(0)").slideToggle(); return false; })))
      .append($("<div>")
        .append($("<input id='debug-tab-flow' type='checkbox'>").click(function() { hub.send("set-debug-tab-flow", { value: !!$(this).attr("checked") }); }))
        .append($("<label>").text("Flowing tabs"))
        .append($("<br>"))
        .append($("<input type='checkbox' checked='checked'>").click(function() { $(".todo").toggleClass("todo-off"); }))
        .append($("<label>").text("Unfinished"))
        .append($("<br>"))
        .append($("<input type='checkbox'>").click(function() { $(".page").toggleClass("visible"); }))
        .append($("<label>").text("Toggle hidden"))
        .append($("<br>"))
        .append($("<input type='checkbox' data-id='proxy' id='debugProxy'>").click(function(e) { ajax.post("/api/proxy-ctrl/" + ($(e.target).prop("checked") ? "on" : "off")).call(); }))
        .append($("<label for='debugProxy'>").text("Proxy enabled"))
        .append($("<br>"))
        .append($("<input type='checkbox' data-id='maps' id='debugMaps'>")
            .click(function(e) { ajax.query("set-feature", {feature: "maps-disabled", value: $(e.target).prop("checked")}).call(); })
            .prop("checked", features.enabled("maps-disabled")))
        .append($("<label for='debugMaps'>").text("Disable maps"))
        .append($("<br>"))
        .append($("<input type='checkbox' data-id='anim' checked='checked'>").click(function() { tree.animation($(this).prop("checked")); }))
        .append($("<label>").text("Animations"))
        .append($("<h3><a href='/api/last-email'>Last Email</a></h3>"))
        .append($("<p>").text("Apply:")
          .append($("<span>").attr("id", "debug-apply-done").css("font-weight", "bold").hide())
          .append($("<a>").attr("id", "debug-apply-minimal").attr("href", "#").text("minimal").click(function() { applyFixture("minimal"); }))
          .append($("<a>").attr("id", "debug-apply-minimal").attr("href", "#").text("municipality-test-users").click(function() { applyFixture("municipality-test-users"); })))
        .append($("<p>").text("Create:")
          .append($("<span>").attr("id", "debug-create-done").css("font-weight", "bold").hide())
          .append($("<a>").attr("id", "debug-create-application").attr("href", "#").text("asuinkerrostalo").click(function() { createApplication("kerrostalo-rivitalo"); })))
        .append($("<span>").attr("id", "debug-apply-done").css("font-weight", "bold").hide())
        .append($("<span>").text("Throttle web: "))
        .append($("<b>").addClass("dev-throttle-web").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='10'>").change(_.throttle(_.partial(throttle, "web"), 500)))
        .append($("<br>"))
        .append($("<span>").text("Throttle DB: "))
        .append($("<b>").addClass("dev-throttle-db").text("0"))
        .append($("<input type='range' value='0' min='0' max='2000' step='10'>").change(_.throttle(_.partial(throttle, "db"), 500))))
      .append($("<h3>")
        .append($("<a>").attr("href", "#").text("Timing").click(function() { $("footer .dev-debug div:eq(1)").slideToggle(); return false; })))
      .append($("<div>")
        .append($("<table>").addClass("dev-debug-timing"))
        .hide()));

  setTimeout(loadTimingData, 10);

  ajax.get(window.location.protocol + "//" + window.location.host + "/perfmon/throttle")
    .success(function(data) {
      var ranges = $("footer .dev-debug input[type='range']");
      $(ranges[0]).val(data.web).change();
      $(ranges[1]).val(data.db).change();
    })
    .call();

  ajax
    .get("/api/proxy-ctrl")
    .success(function(data) {
      $("footer input[data-id='proxy']").prop("checked", data.data);
      // Refresh maps checkbox too, features might not have been loaded when the box was initialized
      $("footer input[data-id='maps']").prop("checked", features.enabled("maps-disabled"));
    })
    .call();

  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };

  // Help registering company:

  $("<div>")
    .css("padding", "8px")
    .css("margin-top", "20px")
    .append($("<button>")
      .addClass("btn btn-decline")
      .html("DEBUG: Auto fill")
      .click(function() {
        var form = $("#register-company .form-group"),
            fill = function(id, value) { $("[data-test-id=register-company-" + id + "]", form).val(value).change(); },
            fillAll = function(f) { _.each(f, function(value, id) { fill(id, value); }); };
        fillAll({
          name:      "Oy FooBar Ab",
          y:         "FI1234567-1",
          reference: "Kansantanhu osasto",
          address1:  "Latokuja 1",
          po:        "Sipoo",
          zip:       "12345",
          country:   "SUAMI",
          ovt:       "0037123456710007",
          pop:       "003776543212",
          firstName: "fo",
          lastName:  "ba",
          email:     "fo@ba.com"
        });
      }))
    .appendTo("#register-company .content");

});
