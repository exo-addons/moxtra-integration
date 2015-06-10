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
				$form.attr("action", $form.attr("action-cancel"));
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
		var spaceName = $app.data("exo-space");
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

						// first show Moxtra pages (iframe loads long)
						var pages = moxtra.moxtrajs().showPages(binderId, null, "moxtra-binder-pages");
						pages.done(function() {
							var $pages = $data.find("#moxtra-binder-pages div");
							$pages.css("width", "100%");
						});
						pages.fail(function(e) {
							log("ERROR: cannot show pages. " + e);
						});

						// init meets
						var $meetPopup = $data.find("#moxtra-binder-meet");
						var $startMenu = $data.find(".dropdown a.meetStartAction");
						var $scheduleMenu = $data.find(".dropdown a.meetScheduleAction");
						var $meetStart = $meetPopup.find(".meetStart");
						var $meetSchedule = $meetPopup.find(".meetSchedule");
						var $form = $meetPopup.find("form");
						var $meetTime = $form.find(".meetTime");
						var $creating = $meetPopup.find(".meetCreating");
						var $created = $meetPopup.find(".meetCreated");

						var $pickers = $form.find(".form_datetime");
						$pickers.each(function(i, el) {
							$(el).datetimepicker({
								autoclose : true,
								todayBtn : true,
								minuteStep : 15,
								pickerPosition : "bottom-left"
							});
						});
						var $startPicker = $pickers.find("input[name='meetStartTime']");
						var startPicker = $startPicker.parent().data("datetimepicker");

						var $endPicker = $pickers.find("input[name='meetEndTime']");
						var endPicker = $endPicker.parent().data("datetimepicker");

						function resetMeet() {
							$creating.hide();
							$created.hide();

							$form.find("input[name='meetTopic']").val("");
							$form.find("textarea[name='meetAgenda']").val("");
							$form.find("input[name='meetAutorec']").val(false);
							$form.find("textarea[name='meetEmails']").val("");

							$form.show("blind", 750);

							// dates
							if ($startPicker.is(":visible")) {
								var startTime = new Date();
								startTime.setMinutes(startTime.getMinutes() + 1);
								startPicker.setDate(startTime);
								var endTime = new Date();
								endTime.setMinutes(startTime.getMinutes() + 30);
								endPicker.setDate(endTime);
							}
						}

						// show and process a meet
						function openMeet(startNow) {
							var $message = $meetPopup.find(".meetMessage");

							// show creating meet pane
							$meetStart.hide();
							$meetSchedule.hide();
							$form.hide("fade");
							$creating.show("blind");

							function showMeet(meet) {
								// show created meet pane
								// meet link opens a page on Moxtra where user can start it
								var $meetLink = $created.find(".meetLink > a");
								$meetLink.attr("href", meet.startMeetUrl);
								$meetLink.text(meet.startMeetUrl);
								
								var $meetEvent = $created.find(".meetEvent > a");
								var calendarLink = window.location.href.replace("/moxtra", "/calendar");
								$meetEvent.attr("href", calendarLink);

								if (startNow) {
									var $startButton = $created.find(".meetStartAction");
									$startButton.find("a").click(function() {
										// use Moxtra JS to open the meet immediately
										moxtra.moxtrajs().startMeet(meet.binderId, {
											end_meet : function() {
												// TODO initiate meet saving here
											},
											error : function(event) {
												showError(event.error_message + " (" + event.error_code + ")", $message);
											}
										});
									});
									$startButton.show();
								}
								$creating.hide("fade");
								$created.show("blind");
							}

							var topic = $form.find("input[name='meetTopic']").val();
							var sameMeet = $meetPopup.data(topic);
							if (sameMeet && Math.abs(sameMeet.startTime.time - startTime.getTime()) < 180000) {
								// use previously created meet
								showMeet(sameMeet);
							} else {
								var agenda = $form.find("textarea[name='meetAgenda']").val();
								var autoRec = $form.find("input[name='meetAutorec']").val();
								//var startTime = $form.find("input[name='meetStartTime']:visible").val();
								var startTime = startPicker.getDate();
								//var endTime = $form.find("input[name='meetEndTime']:visible").val();
								var endTime = endPicker.getDate();
								var participants = $form.find(".meetSpaceMembers select[name='meetParticipants']").val();
								if (!participants) {
									participants = [];
								}
								var moxtraUsers = $form.find(".meetMoxtraContacts select[name='meetParticipants']").val();
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

								// quick meet for about 30min
								if (startTime) {
									try {
										startTime = new Date(startTime);
									} catch(e) {
										startTime = null;
									}
								}
								if (!startTime) {
									startTime = new Date();
									startTime.setMinutes(startTime.getMinutes() + 1);
								}
								if (endTime) {
									try {
										endTime = new Date(endTime);
									} catch(e) {
										endTime = null;
									}
								}
								if (!endTime) {
									endTime = new Date();
									endTime.setMinutes(startTime.getMinutes() + 30);
								}

								// create new meet (TODO look at server-side for the same meet by more wide criteria)
								//var newMeet = moxtra.createBinderMeet(spaceName, topic, agenda, startTime.getTime(), endTime.getTime(), autoRec,
								// participants);
								var newMeet = moxtra.createMeet(topic, agenda, startTime.getTime(), endTime.getTime(), autoRec, participants);
								newMeet.done(function(meet) {
									$meetPopup.data(meet.name, meet);
									//$meetPopup.modal("hide");
									showMeet(meet);
								});
								newMeet.fail(function(e) {
									showError(e.message, $message);
								});
							}
						}

						// start/schedule button actions
						$startMenu.click(function() {
							// Start meet action
							hideError();
							resetMeet();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$meetStart.click(function(ev) {
										ev.preventDefault();
										openMeet(true);
									});
								});
							});
							$meetStart.show();
							$meetTime.hide();
							$meetSchedule.hide();
							$meetPopup.modal("show");
						});
						$scheduleMenu.click(function() {
							// Schedule meet action
							hideError();
							resetMeet();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$meetSchedule.click(function(ev) {
										ev.preventDefault();
										openMeet();
									});
								});
							});
							$meetTime.show();
							$meetSchedule.show();
							$meetStart.hide();
							$meetPopup.modal("show");
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
