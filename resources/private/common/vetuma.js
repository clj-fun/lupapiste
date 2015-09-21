var vetuma = (function() {
  "use strict";

  function wrapHandlers(onFound, onNotFound) {
    return function (resp) {
      return resp && resp.userid ? onFound(resp) : onNotFound(resp);
    };
  }

  function getUser(onFound, onNotFound, onError) {
    ajax.get("/api/vetuma/user").raw(true).success(wrapHandlers(onFound, onNotFound)).error(onError).call();
  }

  return {
    getUser: getUser
  };

})();
