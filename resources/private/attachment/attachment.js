var attachment = (function() {
  "use strict";

  var applicationId = null;
  var uploadingApplicationId = null;
  var attachmentId = null;
  var model = null;

  var authorizationModel = authorization.create();
  var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachment", false);

  function deleteAttachmentFromServer() {
    ajax
      .command("delete-attachment", {id: applicationId, attachmentId: attachmentId})
      .success(function() {
        repository.load(applicationId);
        window.location.hash = "!/application/"+applicationId+"/attachments";
        return false;
      })
      .call();
    return false;
  }

  // this function is mutated over in the attachement.deleteVersion
  var deleteAttachmentVersionFromServerProxy;

  function deleteAttachmentVersionFromServer(fileId) {
    ajax
      .command("delete-attachment-version", {id: applicationId, attachmentId: attachmentId, fileId: fileId})
      .success(function() {
        repository.load(applicationId);
      })
      .error(function() {
        repository.load(applicationId);
      })
      .call();
    return false;
  }

  // These cannot be changed to use LUPAPISTE.ModalDialog.showDynamicYesNo,
  // because the ids are registered with hub.subscribe.
  LUPAPISTE.ModalDialog.newYesNoDialog("dialog-confirm-delete-attachment",
    loc("attachment.delete.header"), loc("attachment.delete.message"), loc("yes"), deleteAttachmentFromServer, loc("no"));
  LUPAPISTE.ModalDialog.newYesNoDialog("dialog-confirm-delete-attachment-version",
    loc("attachment.delete.version.header"), loc("attachment.delete.version.message"), loc("yes"), function() {deleteAttachmentVersionFromServerProxy();}, loc("no"));

  function ApproveModel(authorizationModel) {
    var self = this;

    self.authorizationModel = authorizationModel;

    self.setApplication = function(application) { self.application = application; };
    self.setAuthorizationModel = function(authorizationModel) { self.authorizationModel = authorizationModel; };
    self.setAttachmentId = function(attachmentId) { self.attachmentId = attachmentId; };

    self.stateIs = function(state) {
      var att = self.application &&
        _.find(self.application.attachments,
            function(attachment) {
              return attachment.id === self.attachmentId;
            });
      return att.state === state;
    };

    self.isNotOk = function() { return !self.stateIs("ok");};
    self.doesNotRequireUserAction = function() { return !self.stateIs("requires_user_action");};
    self.isApprovable = function() { return self.authorizationModel.ok("approve-attachment"); };
    self.isRejectable = function() { return self.authorizationModel.ok("reject-attachment"); };

    self.rejectAttachment = function() {
      var id = self.application.id;
      ajax.command("reject-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function() {
          repository.load(id);
        })
        .error(function() {
          repository.load(id);
        })
        .call();
      return false;
    };

    self.approveAttachment = function() {
      var id = self.application.id;
      ajax.command("approve-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function() {
          repository.load(id);
        })
        .error(function() {
          repository.load(id);
        })
        .call();
      return false;
    };
  }

  var approveModel = new ApproveModel(authorizationModel);

  model = {
    id:   ko.observable(),
    application: {
      id:     ko.observable(),
      title:  ko.observable(),
      state:  ko.observable()
    },
    applicationState:     ko.observable(),
    authorized:           ko.observable(false),
    filename:             ko.observable(),
    latestVersion:        ko.observable({}),
    versions:             ko.observable([]),
    signatures:           ko.observableArray([]),
    type:                 ko.observable(),
    attachmentType:       ko.observable(),
    allowedAttachmentTypes: ko.observableArray([]),
    previewDisabled:      ko.observable(false),
    operation:            ko.observable(),
    selectedOperationId:  ko.observable(),
    selectableOperations: ko.observableArray(),
    contents:             ko.observable(),
    scale:                ko.observable(),
    scales:               ko.observableArray(LUPAPISTE.config.attachmentScales),
    size:                 ko.observable(),
    sizes:                ko.observableArray(LUPAPISTE.config.attachmentSizes),
    subscriptions:        [],
    indicator:            ko.observable().extend({notify: "always"}),
    showAttachmentVersionHistory: ko.observable(),
    showHelp:             ko.observable(false),
    init:                 ko.observable(false),

    toggleHelp: function() {
      model.showHelp(!model.showHelp());
    },

    hasPreview: function() {
      return !model.previewDisabled() && (model.isImage() || model.isPdf() || model.isPlainText());
    },

    isImage: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      var contentType = version.contentType;
      return contentType && contentType.indexOf("image/") === 0;
    },

    isPdf: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      return version.contentType === "application/pdf";
    },

    isPlainText: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      return version.contentType === "text/plain";
    },

    newAttachmentVersion: function() {
      initFileUpload({
        applicationId: applicationId,
        attachmentId: attachmentId,
        attachmentType: model.attachmentType(),
        typeSelector: false
      });

      model.previewDisabled(true);

      // Upload dialog is opened manually here, because click event binding to
      // dynamic content rendered by Knockout is not possible
      LUPAPISTE.ModalDialog.open("#upload-dialog");
    },

    deleteAttachment: function() {
      model.previewDisabled(true);
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-attachment");
    },

    showChangeTypeDialog: function() {
      LUPAPISTE.ModalDialog.open("#change-type-dialog");
    },

    deleteVersion: function(fileModel) {
      var fileId = fileModel.fileId;
      deleteAttachmentVersionFromServerProxy = function() { deleteAttachmentVersionFromServer(fileId); };
      model.previewDisabled(true);
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-attachment-version");
    },

    sign: function() {
      model.previewDisabled(true);
      signingModel.init({id: applicationId, attachments:[model]});
    },

    toggleAttachmentVersionHistory: function() {
      model.showAttachmentVersionHistory(!model.showAttachmentVersionHistory());
    }
  };

  model.name = ko.computed(function() {
    if (model.attachmentType()) {
      return "attachmentType." + model.attachmentType();
    }
    return null;
  });

  model.editable = ko.computed(function() {
    return _.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.application.state)) ?
             currentUser.isAuthority() || _.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.applicationState)) :
             true;
  });


  function saveLabelInformation(type, data) {
    data.id = applicationId;
    data.attachmentId = attachmentId;
    ajax
      .command("set-attachment-meta", data)
      .success(function() {
        model.indicator(type);
      })
      .error(function(e) {
        error(e.text);
      })
      .call();
  }

  function subscribe() {
    model.subscriptions.push(model.attachmentType.subscribe(function(attachmentType) {
      var type = model.type();
      var prevAttachmentType = type["type-group"] + "." + type["type-id"];
      var loader$ = $("#attachment-type-select-loader");
      if (prevAttachmentType !== attachmentType) {
        loader$.show();
        ajax
          .command("set-attachment-type",
            {id:              applicationId,
             attachmentId:    attachmentId,
             attachmentType:  attachmentType})
          .success(function() {
            loader$.hide();
            repository.load(applicationId);
          })
          .error(function(e) {
            loader$.hide();
            repository.load(applicationId);
            error(e.text);
          })
          .call();
      }
    }));

    function applySubscription(label) {
      model.subscriptions.push(model[label].subscribe(_.debounce(function(value) {
        if (value || value === "") {
          var data = {};
          data[label] = value;
          saveLabelInformation(label, {meta: data});
        }
      }, 500)));
    }

    model.subscriptions.push(model.selectedOperationId.subscribe(function(id) {
      if (!model.operation() || id !== model.operation().id) {
        var op = _.findWhere(model.selectableOperations(), {id: id});
        op = op || null;
        saveLabelInformation("operation", {meta: {op: op}});
      }
    }));

    applySubscription("contents");
    applySubscription("scale");
    applySubscription("size");
  }

  function unsubscribe() {
    while(model.subscriptions.length !== 0) {
      model.subscriptions.pop().dispose();
    }
  }

  function showAttachment(applicationDetails) {
    var application = applicationDetails.application;
    if (!applicationId || !attachmentId) { return; }
    var attachment = _.find(application.attachments, function(value) {return value.id === attachmentId;});
    if (!attachment) {
      error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
      return;
    }

    $("#file-preview-iframe").attr("src","");

    var isUserAuthorizedForAttachment = attachment.required ? currentUser.get().role() === "authority" : true;
    model.authorized(isUserAuthorizedForAttachment);

    model.latestVersion(attachment.latestVersion);
    model.versions(attachment.versions);
    model.signatures(attachment.signatures || []);
    model.filename(attachment.filename);
    model.type(attachment.type);
    model.selectableOperations(application.operations);
    model.operation(attachment.op);
    model.selectedOperationId(attachment.op ? attachment.op.id : undefined);
    model.contents(attachment.contents);
    model.scale(attachment.scale);
    model.size(attachment.size);
    model.applicationState(attachment.applicationState);

    var type = attachment.type["type-group"] + "." + attachment.type["type-id"];
    model.attachmentType(type);
    model.allowedAttachmentTypes(application.allowedAttachmentTypes);

    // Knockout works poorly with dynamic options.
    // To avoid headaches, init the select and update the ko model manually.
    var selectList$ = $("#attachment-type-select");
    attachmentTypeSelect.initSelectList(selectList$, application.allowedAttachmentTypes, model.attachmentType());
    selectList$.change(function(e) {model.attachmentType($(e.target).val());});

    model.application.id(applicationId);
    model.application.title(application.title);
    model.application.state(application.state);
    model.id = attachmentId;

    approveModel.setApplication(application);
    approveModel.setAttachmentId(attachmentId);

    model.showAttachmentVersionHistory(false);

    pageutil.hideAjaxWait();
    model.indicator(false);

    authorizationModel.refresh(application, {attachmentId: attachmentId}, function() {
      model.init(true);
      if (!model.latestVersion()) {
        setTimeout(function() {
          model.showHelp(true);
        }, 1500);
      }
    });
  }

  hub.onPageLoad("attachment", function(e) {
    pageutil.showAjaxWait();
    model.init(false);
    model.showHelp(false);
    applicationId = e.pagePath[0];
    attachmentId = e.pagePath[1];
    repository.load(applicationId);
  });

  repository.loaded(["attachment"], function(application, applicationDetails) {
    if (applicationId === application.id) {
      unsubscribe();
      showAttachment(applicationDetails);
      subscribe();
    }
  });

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("data-src");
    $("#uploadFrame").attr("src", originalUrl);
  }

  hub.subscribe("upload-cancelled", LUPAPISTE.ModalDialog.close);

  hub.subscribe({type: "dialog-close", id : "upload-dialog"}, function() {
    resetUploadIframe();
    model.previewDisabled(false);
  });
  hub.subscribe({type: "dialog-close", id : "dialog-confirm-delete-attachment"}, function() {
    model.previewDisabled(false);
  });
  hub.subscribe({type: "dialog-close", id : "dialog-confirm-delete-attachment-version"}, function() {
    model.previewDisabled(false);
  });
  hub.subscribe({type: "dialog-close", id : "dialog-sign-attachment"}, function() {
    model.previewDisabled(false);
  });

  $(function() {
    $("#attachment").applyBindings({
      attachment: model,
      approve: approveModel,
      authorization: authorizationModel
    });
    $("#upload-page").applyBindings({});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: authorizationModel});

    // Iframe content must be loaded AFTER parent JS libraries are loaded.
    // http://stackoverflow.com/questions/12514267/microsoft-jscript-runtime-error-array-is-undefined-error-in-ie-9-while-using
    resetUploadIframe();
  });

  function uploadDone() {
    if (uploadingApplicationId) {
      repository.load(uploadingApplicationId);
      LUPAPISTE.ModalDialog.close();
      uploadingApplicationId = null;
    }
  }

  hub.subscribe("upload-done", uploadDone);

  // applicationId, attachmentId, attachmentType, typeSelector, target, locked, authority
  function initFileUpload(options) {
    uploadingApplicationId = options.applicationId;
    var iframeId = "uploadFrame";
    var iframe = document.getElementById(iframeId);
    iframe.contentWindow.LUPAPISTE.Upload.init(options);
  }

  function regroupAttachmentTypeList(types) {
    return _.map(types, function(v) { return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })}; });
  }

  return {
    initFileUpload: initFileUpload,
    regroupAttachmentTypeList: regroupAttachmentTypeList
  };

})();
