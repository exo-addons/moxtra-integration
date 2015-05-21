/**
 * Javascript for Moxtra support in Social spaces
 */

(function($, moxtra) {
	/**
	 * Logger to browser console.
	 */
	function log(msg, e) {
		if ( typeof console != "undefined" && typeof console.log != "undefined") {
			console.log(msg);
			if (e && typeof e.stack != "undefined") {
				console.log(e.stack);
			}
		}
	}

	$(function() {
		var $message = $("#moxtra-binder-message");

		function showError(msg) {
			var text;
			if ( msg instanceof $) {
				text = msg.text();
				$message.empty();
				msg.detach().appendTo($message);
			} else {
				text = msg;
				$message.html(msg);
			}
			log("ERROR: " + text);
			$message.addClass("alert-error");
			$message.show();
			//$message.toggle("blind");
		}

		function hideError() {
			$message.empty();
			$message.removeClass("alert-error");
			//$message.hide();
			$message.toggle("blind");
		}

		var messageCloser;
		function showSuccess(msg) {
			$message.html(msg);
			$message.addClass("alert-success");
			//$message.show();
			$message.toggle("blind");
			messageCloser = setTimeout(function() {
				clearTimeout(messageCloser);
				$message.empty();
				$message.removeClass("alert-success");
				$message.toggle("blind");
			}, 5000);
		}

		var $binderSettings = $("#moxtra-binder-settings div");
		var $enableBinder = $binderSettings.find("input[name='enableBinder']");
		var $binderEditor = $("#moxtra-binder-editor");

		// init user:
		// use data set on enable checkbox in index template
		// if userName not found it means that user unauthorized
		var userName = $enableBinder.data("moxtra-user");
		var authLink = $enableBinder.data("moxtra-authlink");
		moxtra.initUser(userName, userName, authLink);

		// form cancel (reset)
		$binderSettings.find("button.binder-cancel").click(function() {
			hideError();
			$binderSettings.attr("action", $binderSettings.attr("action-cancel"));
			$binderSettings.submit();
		});

		if ($enableBinder.is(":checked")) {
			// load current binder name for enabled
			$binderEditor.jzLoad("MoxtraBinderSpaceController.binder()", function(response, status, jqXHR) {
				// handle error response
				var $msg = $binderEditor.find(".message-text");
				if ($msg.length > 0) {
					showError($msg);
				}
			});
		};

		// authentication
		$enableBinder.change(function(ev) {
			if (!$enableBinder.data("moxtra-binder-enabling")) {
				if ($enableBinder.is(":checked")) {
					if (moxtra.isAuthorized()) {
						// load binder settings fields on demand
						$binderEditor.jzLoad("MoxtraBinderSpaceController.binder()", function(response, status, jqXHR) {
							// handle error response
							var $msg = $binderEditor.find(".message-text");
							if ($msg.length > 0) {
								showError($msg);
							} else {
								$binderEditor.find("input[value='_existing']").click(function() {
									var $binderSelector = $("#moxtra-binder-selector");
									if ($binderSelector.children().size() == 0) {
										$binderSelector.jzLoad("MoxtraBinderSpaceController.bindersList()", function(response, status, jqXHR) {
											var $msg = $binderEditor.find(".message-text");
											if ($msg.length > 0) {
												showError($msg);
											}
										});
									}
								});
								$binderSettings.find(".binder-config").removeClass("disabled");
							}
						});
					} else {
						// need auth user first
						ev.preventDefault();
						$enableBinder.attr('checked', false);
						var auth = moxtra.authorize();
						auth.done(function() {
							$enableBinder.click();
						});
						auth.fail(function(error) {
							log("Moxtra authorization error " + error);
							// TODO notify the error to an user
							showError("Authorization error. " + error);
						});
						auth.always(function() {
							$enableBinder.data("moxtra-binder-enabling", false);
						});
					}
				} else {
					$binderSettings.find(".binder-config").addClass("disabled");
				}
			}
		});

		return {};
	});
})($, exoMoxtra);
