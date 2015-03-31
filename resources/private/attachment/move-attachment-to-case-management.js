(function() {
  "use strict";

  var pageName  = "move-attachments-to-case-management-select";
  var eventName = "start-moving-attachments-to-case-management";
  var command   = "move-attachments-to-case-management";

  var multiSelect = _.extend(new LUPAPISTE.AttachmentMultiSelect(), {});

  multiSelect.hash = "!/" + pageName + "/";

  var doMoveAttachmets = function(selectedAttachmentsIds) {
    var id = multiSelect.model.appModel.id();
    ajax.command(command, {
      id: id,
      lang: loc.getCurrentLanguage(),
      attachmentIds: selectedAttachmentsIds
    })
    .success(function() {
      window.location.hash = "!/application/" + id + "/attachments";
      repository.load(id);
    })
    .error(function() {
      notify.error(loc("error.dialog.title"), loc("application.attachmentsMoveToCaseManagement.error"));
      repository.load(id);
    })
    .call();
  };

  multiSelect.model.moveAttachmets = function(selectedAttachmentsIds) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("application.attachmentsMoveToCaseManagement"),
      loc("application.attachmentsMoveToCaseManagement.confirmationMessage"),
      {title: loc("yes"), fn: _.partial(doMoveAttachmets, selectedAttachmentsIds)},
      {title: loc("no")}
    );
  };

  multiSelect.filterAttachments = function(attachments) {
    return _(attachments)
      .filter(function(a) {
        return (a.versions.length > 0 &&
        	(!a.sent || _.last(a.versions).created > a.sent) &&
        	!(_.includes(["verdict", "statement"], util.getIn(a, ["target", "type"]))));
      })
      .each(function(a) {
        a.selected = true;
      })
      .value();
  };

  hub.onPageLoad(pageName, function() {
    multiSelect.onPageLoad();
  });

  hub.onPageUnload(pageName, function() {
    multiSelect.onPageUnload();
  });

  hub.subscribe(eventName, function() {
    multiSelect.subscribe();
  });

  $(function() {
    $("#" + pageName).applyBindings(multiSelect.model);
  });
})();
