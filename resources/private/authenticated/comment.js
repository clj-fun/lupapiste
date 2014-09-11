var comments = (function() {
  // "use strict";

  function CommentModel(takeAll, newCommentRoles) {
    var self = this;

    self.applicationId = null;
    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.to = ko.observable();
    self.showAttachmentComments = ko.observable(false);
    self.showPreparationComments = ko.observable(false);

    self.refresh = function(application, target) {
      console.log("refresh comments", target);
      self.applicationId = application.id;
      self.target(target || {type: "application"}).text("");
      console.log("target", self.target());
      var filteredComments =
        _.filter(application.comments,
            function(comment) {
              console.log("filter", self.target().type === comment.target.type, self.target().id === comment.target.id, takeAll);
              return takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
            });
      console.log("commentsit", filteredComments);
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.isForMe = function(model) {
      return model.to && model.to.id && model.to.id() === currentUser.id();
    };

    self.disabled = ko.computed(function() {
      return self.processing() || _.isEmpty(self.text());
    });


    var doAddComment = function(markAnswered, openApplication) {
        ajax.command("add-comment", {
            id: self.applicationId,
            text: self.text(),
            target: self.target(),
            to: self.to(),
            roles: newCommentRoles || ["applicant","authority"],
            "mark-answered": markAnswered,
            openApplication: openApplication
        })
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
            self.text("").to(undefined);
            if (markAnswered) {
                LUPAPISTE.ModalDialog.showDynamicOk(loc('comment-request-mark-answered-label'), loc('comment-request-mark-answered.ok'));
            }
            repository.load(self.applicationId);
        })
        .call();
        return false;
    };

    self.markAnswered = function() {
      return doAddComment(true, false);
    };

    self.submit = function() {
      return doAddComment(false, false);
    };

    self.stateOpenApplication = function() {
      return doAddComment(false, true);
    };

    self.isForNewAttachment = function(model) {
      return model && model.target && model.target.version && true;
    };
    self.isAuthorityComment = function(model) {
      return model.user && model.user.role && model.user.role() === "authority";
    };
    self.isForAttachment = function(model) {
      return model && model.target && model.target.type && model.target.type() === "attachment";
    };

    function isPreparationComment(model) {
      return model && model.roles().length === 1 && model.roles()[0] === "authority";
    }

    self.isVisible = function(model) {
      return !takeAll ||
               ((!self.isForNewAttachment(model) || self.showAttachmentComments() ) &&
                (!isPreparationComment(model)    || self.showPreparationComments()));
    };

  }

  return {
    create: function(takeAll, newCommentRoles) {
      return new CommentModel(takeAll, newCommentRoles); }
  };

})();
