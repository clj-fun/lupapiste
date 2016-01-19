LUPAPISTE.StatementEditModel = function(params) {
  "use strict";
  var self = this;

  self.tab = "statement";

  self.authModel = params.authModel;

  var applicationId = params.applicationId;

  self.applicationTitle = params.applicationTitle;
  self.data = params.data;
  
  self.selectedStatus = ko.observable();
  self.text = ko.observable();

  var statementId = ko.pureComputed(function() {
    return util.getIn(self.data, ["id"]);
  });

  self.data.subscribe(function() {
    self.selectedStatus(util.getIn(self.data, ["status"]));
    self.text(util.getIn(self.data, ["text"]));
  });

  var commands = params.commands;

  self.statuses = ko.observableArray([]);

  var submitAllowed = ko.pureComputed(function() {
    return !!self.selectedStatus() && !!self.text() && !_.isEmpty(self.text());
  });

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(commands["submit"]);
  });

  self.isDraft = ko.pureComputed(function() {
    return _.contains(["requested", "draft"], util.getIn(self.data, ["state"]));
  });

  self.showStatement = ko.pureComputed(function() {
    return self.isDraft() ? self.enabled() : true;
  })

  self.coverNote = ko.pureComputed(function() {
    var isStatementGiver = util.getIn(self.data, ["person", "userId"]) === lupapisteApp.models.currentUser.id();
    return self.tab === "statement" && isStatementGiver ? util.getIn(self.data, ["saateText"]) : "";
  });

  self.text.subscribe(function(value) {
    if(util.getIn(self.data, ["text"]) !== value) { 
      hub.send("statement::changed", {tab: self.tab, path: ["text"], value: self.text()});
    }
  });

  self.selectedStatus.subscribe(function(value) {
    if(util.getIn(self.data, ["status"]) !== value) {
      hub.send("statement::changed", {tab: self.tab, path: ["status"], value: self.selectedStatus()});
    }
  });

  hub.send("statement::submitAllowed", {tab: self.tab, value: submitAllowed()})

  submitAllowed.subscribe(function(value) {
    hub.send("statement::submitAllowed", {tab: self.tab, value: value});
  });

  function initStatementStatuses(appId) {
    ajax
    .query("get-possible-statement-statuses", {id: appId})
    .success(function(resp) {
      var sorted = _(resp.data)
        .map(function(item) { return {id: item, name: loc(["statement", item])}; })
        .sortBy("name")
        .value();
      self.statuses(sorted);
    })
    .call();
  }

  if(applicationId()) {
    initStatementStatuses(applicationId());
  } else {
    applicationId.subscribe(function(appId) {
      if (appId && _.isEmpty(self.statuses())) {
        self.statuses.push("");
        initStatementStatuses(appId);
      }
    });
  }
};
