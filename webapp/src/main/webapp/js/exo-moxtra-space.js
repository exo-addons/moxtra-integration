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

	// Do the work on fully loaded page (required for Bootstrap's tooltip initialization after CSS rendering)
	$(window).load(function() {
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
			// $message.toggle("blind");
		}

		function hideError() {
			$message.empty();
			$message.removeClass("alert-error");
			// $message.hide();
			$message.toggle("blind");
		}

		var messageCloser;
		function showMessage(msg, type) {
			$message.html(msg);
			$message.addClass("alert-" + type);
			// $message.show();
			$message.toggle("blind");
			messageCloser = setTimeout(function() {
				clearTimeout(messageCloser);
				$message.empty();
				$message.removeClass("alert-" + type);
				$message.toggle("blind");
			}, 5000);
		}

		// settings popup if available
		var $settings = $("#moxtra-binder-settings");
		var $settingsButton = $("#moxtra-binder-settings-button");
		if ($settings.size() > 0) {
			// TODO where it does work?
			var $message = $settings.find(".binderMessage");
			var $form = $settings.find("form");
			var $enable = $form.find("input[name='enableBinder']");
			var $editor = $form.find(".binderConfig");

			// form cancel (reset)
			$form.find("button.binderCancel").click(function() {
				hideError();
				$form.attr("action", $settings.attr("action-cancel"));
				$form.submit();
			});

			// enable change action
			$enable.change(function(ev) {
				if (!$enable.data("moxtra-binder-enabling")) {
					if ($enable.is(":checked")) {
						if (moxtra.isAuthorized()) {
							// load binder settings fields on demand
							$editor.jzLoad("MoxtraBinderSpaceController.binder()", function(response, status, jqXHR) {
								var $msg = $editor.find(".messageText");
								if ($msg.length > 0) {
									showError($msg);
								} else {
									$editor.find("input[value='_existing']").click(function() {
										var $binderSelector = $editor.find(".binderSelector");
										if ($binderSelector.children().size() == 0) {
											$binderSelector.jzLoad("MoxtraBinderSpaceController.bindersList()", function(response, status, jqXHR) {
												var $msg = $editor.find(".messageText");
												if ($msg.length > 0) {
													showError($msg);
												}
											});
										}
									});
									$form.find(".binderConfig").removeClass("disabled");
								}
							});
						} else {
							// need auth user first
							ev.preventDefault();
							$enable.attr("checked", false);
							showError("Moxtra authorization required");
						}
					} else {
						$form.find(".binderConfig").addClass("disabled");
					}
				}
			});

			$settingsButton.find("a").click(function() {
				//$settings.jzLoad("MoxtraBinderSpaceController.settingsPopup()", function(response, status, jqXHR) {
				// var $msg = $settings.find(".messageText");
				// if ($msg.length > 0) {
				// showError($msg);
				// } else {
				//}

				// load current binder name if it is already enabled
				if ($enable.is(":checked")) {
					$editor.jzLoad("MoxtraBinderSpaceController.binder()", function(response, status, jqXHR) {
						var $msg = $editor.find(".messageText");
						if ($msg.length > 0) {
							showError($msg);
						}
					});
				}

				//$settings.modal("show");
				$("#moxtra-binder-settings").modal({
					show : true,
					backdrop : false
				});
				//});
			});
		}

		var $app = $("#moxtra-binder-app");
		var $data = $("#moxtra-binder-data");

		// init tooltips
		$app.find("a").tooltip();

		// init user:
		// use data set on enable checkbox in index template
		// if userName not found it means that user unauthorized
		var userName = $app.data("exo-user");
		var authorized = $app.data("moxtra-authorized");
		var authLink = $app.data("moxtra-authlink");
		moxtra.initUser(userName, authorized, authLink);

		function loadData() {
			// show settings button
			$settingsButton.show();
			var binderId = $app.data("moxtra-binder-id");
			if (!binderId) {
				// FYI if no binderId then binder not enabled: we shop a tip for an user where to enable
				// it
				$settingsButton.find("a").tooltip("show");
			} else {
				// load binder data (meet button, pages zone etc)
				$data.jzLoad("MoxtraBinderSpaceController.binderData()", function(response, status, jqXHR) {
					// handle error response
					var $msg = $data.find(".messageText");
					if ($msg.length > 0) {
						showError($msg);
					} else {
						$data.show();

						// init meet button
						$data.find("a.meetStartAction").click(function() {
							// var newMeet = moxtra.createMeet();
							// newMeet.done(function(meet) {
							// showMeet(meet);
							// });
							// newMeet.fail(function() {
							// meetWindow.close();
							// });

							// use Moxtra JS
							moxtra.moxtrajs().startMeet();
						});

						// TODO invoke Moxtra JS showPage()
						//var $pages = $data.find("#moxtra-binder-pages");
						var pages = moxtra.moxtrajs().showPages(binderId, null, "moxtra-binder-pages");
						pages.done(function() {
							var $pages = $data.find("#moxtra-binder-pages div");
							$pages.css("width", "100%");
						});
						pages.fail(function(e) {
							log("ERROR: cannot show pages. " + e);
						});
					}
				});
			}
		}

		if (moxtra.isAuthorized()) {
			loadData();
		} else {
			// show authz button and label "Authorization required"
			var $authButton = $("#moxtra-auth-button");
			$authButton.click(function() {
				var auth = moxtra.authorize();
				auth.done(function() {
					log("Moxtra user authorized successfully");
					loadData();
				});
				auth.fail(function(error) {
					if (error == "Canceled") {
						showMessage("Authorization canceled.", "info");
					} else {
						log("Moxtra authorization error " + error);
						showError("Authorization error. " + error);
					}
				});
				auth.always(function() {
					// TODO smth here?
				});
			});
			$app.find("a").tooltip();
			$authButton.show();
			$authButton.find("a").tooltip("show");
		}

		return {};
	});
})($, exoMoxtra);
