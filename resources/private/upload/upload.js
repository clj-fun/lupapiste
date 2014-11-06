var LUPAPISTE = LUPAPISTE || {};
LUPAPISTE.Upload = {
  fileExtensions: LUPAPISTE.config.fileExtensions.join(", "),
  applicationId: ko.observable(),
  attachmentId: ko.observable(),
  attachmentType: ko.observable(),
  attachmentTypeGroups: ko.observableArray(),
  typeSelector: ko.observable(false),
  errorMessage: ko.observable(),
  targetType: ko.observable(),
  targetId: ko.observable(),
  locked: ko.observable(),
  authority: ko.observable()
};

LUPAPISTE.Upload.setModel = function(options) {
  "use strict";
  LUPAPISTE.Upload.applicationId(options.applicationId);
  LUPAPISTE.Upload.attachmentId(options.attachmentId);
  LUPAPISTE.Upload.attachmentType(options.attachmentType);
  LUPAPISTE.Upload.typeSelector(options.typeSelector ? true : false);
  LUPAPISTE.Upload.errorMessage(options.errorMessage);
  LUPAPISTE.Upload.targetType(options.target ? options.target.type : null);
  LUPAPISTE.Upload.targetId(options.target ? options.target.id : null);
  LUPAPISTE.Upload.locked(options.locked || false);
  LUPAPISTE.Upload.authority(options.authority || false);
};

LUPAPISTE.Upload.loadTypes = function(applicationId) {
  "use strict";

  if (applicationId) {
    ajax
      .query("attachment-types",{id: applicationId})
      .success(function(d) {
        // fix for IE9 not showing the last option
        if($.browser.msie) {
          d.attachmentTypes.push(["empty", []]);
        }
        LUPAPISTE.Upload.attachmentTypeGroups(_.map(d.attachmentTypes, function(v) {
          return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })};
        }));
        var uploadForm$ = $("#attachmentUploadForm");
        uploadForm$.applyBindings(LUPAPISTE.Upload);
        $("#initLoader").hide();
        uploadForm$.show();
      })
      .call();
  }
};

LUPAPISTE.Upload.init = function(options) {
  "use strict";
  LUPAPISTE.Upload.setModel(options);
  LUPAPISTE.Upload.loadTypes(options.applicationId);
};

LUPAPISTE.Upload.initFromURLParams = function() {
  "use strict";
  if (location.search) {
    var applicationId = pageutil.getURLParameter("applicationId");
    options = {
      applicationId: applicationId, 
      attachmentId: pageutil.getURLParameter("attachmentId"),
      attachmentType: pageutil.getURLParameter("attachmentType"),
      typeSelector: pageutil.getURLParameter("typeSelector"),
      errorMessage: pageutil.getURLParameter("errorMessage"),
      target: {type: pageutil.getURLParameter("targetType"), 
               id: pageutil.getURLParameter("targetId")},
      locked: pageutil.getURLParameter("locked"),
      authority: pageutil.getURLParameter("authority") 
    }
    LUPAPISTE.Upload.setModel(options);
    LUPAPISTE.Upload.loadTypes(applicationId);
  }
};

$(LUPAPISTE.Upload.initFromURLParams);
