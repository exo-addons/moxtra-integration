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

		function showError(msg, elem) {
			var text;
			var $place = elem ? $(elem) : $message;
			if ( msg instanceof $) {
				text = msg.text();
				$place.empty();
				msg.detach().appendTo($place);
			} else {
				text = msg;
				$place.html(msg);
			}
			log("ERROR: " + text);
			$place.addClass("alert-error");
			$place.show();
			// $message.toggle("blind");
		}

		function hideError(elem) {
			var $place = elem ? $(elem) : $message;
			$place.empty();
			$place.removeClass("alert-error");
			// $place.hide();
			$place.toggle("blind");
		}

		var messageCloser;
		function showMessage(msg, type, elem) {
			var $place = elem ? $(elem) : $message;
			$place.html(msg);
			$place.addClass("alert-" + type);
			// $place.show();
			$place.toggle("blind");
			messageCloser = setTimeout(function() {
				clearTimeout(messageCloser);
				$place.empty();
				$place.removeClass("alert-" + type);
				$place.toggle("blind");
			}, 5000);
		}

		// settings popup if available
		var $settings = $("#moxtra-binder-settings");
		var $settingsButton = $("#moxtra-binder-settings-button");
		if ($settings.size() > 0) {
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
				// load current binder name if it is already enabled
				if ($enable.is(":checked")) {
					$editor.jzLoad("MoxtraBinderSpaceController.binder()", function(response, status, jqXHR) {
						var $msg = $editor.find(".messageText");
						if ($msg.length > 0) {
							showError($msg);
						}
					});
				}

				$("#moxtra-binder-settings").modal({
					show : true,
					backdrop : false
				});
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

						// init meets
						var $meetPopup = $("#moxtra-binder-meet");
						var $form = $meetPopup.find("form");
						$form.submit(function(ev) {
							ev.preventDefault();
							var $message = $meetPopup.find(".meetMessage");
							var topic = $form.find("input[name='meetTopic']").val();
							var agenda = $form.find("textarea[name='meetAgenda']").val();
							var autoRec = $form.find("input[name='meetAutorec']").val();
							var startTime = $form.find("input[name='meetStartTime']:visible").val();
							var endTime = $form.find("input[name='meetEndTime']:visible").val();
							var participants = $form.find("meetSpaceMembers select[name='meetParticipants']").val();
							var moxtraUsers = $form.find("meetMoxtraContacts select[name='meetParticipants']").val();
							if (moxtraUsers) {
								// merge participants: remove duplicates
								nextUser :
								for (var mi = 0; mi < moxtraUsers.length; mi++) {
									var moxtraUser = moxtraUsers[mi];
									for (var pi = 0; pi < participants.length; pi++) {
										if (participants[pi] == moxtraUser) {
											continue nextUser;
										}
									}
									participants.push(moxtraUser);
								}
							}

							function showMeet(meet) {
								// use Moxtra JS to open the meet
								moxtra.moxtrajs().startMeet(meet.binderId, {
									end_meet : function() {
										// TODO initiate meet saving here
									},
									error : function(event) {
										showError(event.error_message + " (" + event.error_code + ")", $message);
									}
								});
							}

							// quick meet for about 30min
							if (!startTime) {
								startTime = new Date();
								startTime.setMinutes(startTime.getMinutes() + 1);
							}
							if (!endTime) {
								endTime = new Date();
								endTime.setMinutes(startTime.getMinutes() + 30);
							}
							var sameMeet = $meetPopup.data(topic);
							if (sameMeet && Math.abs(sameMeet.startTime.time - startTime.getTime()) < 30000) {
								// use previously created meet
								showMeet(sameMeet);
							} else {
								// create new meet (TODO look at server-side for the same meet by more wide criteria)
								var newMeet = moxtra.createMeet(topic, agenda, startTime.getTime(), endTime.getTime(), autoRec, participants);
								newMeet.done(function(meet) {
									$meetPopup.data(meet.name, meet);
									$meetPopup.modal("hide");
									showMeet(meet);
								});
								newMeet.fail(function(e) {
									showError(e.message, $message);
								});
							}
						});

						$data.find("a.meetStartAction").click(function() {
							// Start meet action
							hideError();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$form.find(".meetStart").show();
									$form.find(".meetTime").hide();
									$form.find(".meetSchedule").hide();
									$meetPopup.modal("show");
								});
							});
						});

						$data.find("a.meetScheduleAction").click(function() {
							// Schedule meet action
							hideError();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$form.find(".meetTime").show();
									$form.find(".meetSchedule").show();
									$form.find(".meetStart").hide();
									$meetPopup.modal("show");
								});
							});
						});

						// invoke Moxtra JS showPage()
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
					$authButton.hide();
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
