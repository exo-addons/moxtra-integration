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
						var $startMenu = $data.find(".dropdown a.meetStartMenu");
						var $scheduleMenu = $data.find(".dropdown a.meetScheduleMenu");
						var $meetStart = $meetPopup.find(".meetStart");
						var $meetSchedule = $meetPopup.find(".meetSchedule");
						var $form = $meetPopup.find("form");
						var $meetTime = $form.find(".meetTime");
						var $creating = $meetPopup.find(".meetCreating");
						var $created = $meetPopup.find(".meetCreated");

						var $pickers = $form.find(".form_datetime");
						var timeStep = 10;
						$pickers.each(function(i, el) {
							$(el).datetimepicker({
								autoclose : true,
								todayBtn : true,
								minuteStep : timeStep,
								pickerPosition : "bottom-left"
							});
						});
						var $startPicker = $pickers.find("input[name='meetStartTime']");
						var startPicker = $startPicker.parent().data("datetimepicker");
						var $endPicker = $pickers.find("input[name='meetEndTime']");
						var endPicker = $endPicker.parent().data("datetimepicker");
						// auto-adjust dates to avoid wrong values
						$startPicker.parent().on("changeDate", function(e) {
							var now = new Date();
							var startTime = startPicker.getDate();
							if (startTime.getTime() - now.getTime() <= 0) {
								now.setMinutes(now.getMinutes() + 5);
								startTime = now;
								startPicker.setDate(startTime);
							}
							var endTime = endPicker.getDate();
							if (endTime.getTime() - startTime.getTime() <= 0) {
								var newEndTime = new Date(startTime.getTime());
								newEndTime.setMinutes(newEndTime.getMinutes() + (timeStep * 2));
								endPicker.setDate(newEndTime);
								//$endPicker.datetimepicker({
								//	initialDate : newEndTime,
								//});
								log("meetEndTime fixed: " + e.date.toString() + " -> " + newEndTime.toString());
							}
						});
						$endPicker.parent().on("changeDate", function(e) {
							var startTime = startPicker.getDate();
							var endTime = endPicker.getDate();
							if (endTime.getTime() - startTime.getTime() <= 0) {
								var newStartTime = new Date(endTime.getTime());
								newStartTime.setMinutes(newStartTime.getMinutes() - timeStep);
								startPicker.setDate(newStartTime);
								//$startPicker.datetimepicker({
								//	initialDate : newStartTime,
								//});
								log("meetStartTime fixed: " + e.date.toString() + " -> " + newStartTime.toString());
							}
						});

						function resetMeet() {
							$creating.hide();
							$created.hide();

							$form.find("input[name='meetTopic']").val("");
							$form.find("textarea[name='meetAgenda']").val("");
							$form.find("input[name='meetAutorec']").attr("checked", false);
							$form.find("textarea[name='meetEmails']").val("");

							$form.show("blind", 750);

							// dates
							//if ($startPicker.is(":visible")) {
								var startTime = new Date();
								startTime.setMinutes(startTime.getMinutes() + 5);
								startPicker.setDate(startTime);
								/*$startPicker.parent().datetimepicker({
									initialDate : startTime,
								});*/
								var endTime = new Date();
								endTime.setMinutes(startTime.getMinutes() + 35);
								endPicker.setDate(endTime);
								/*$endPicker.parent().datetimepicker({
									initialDate : endTime,
								});*/
							//}
						}

						// show and process a meet
						function openMeet(startNow) {
							var $message = $meetPopup.find(".meetMessage");

							// validation first
							var topic = $form.find("input[name='meetTopic']").val();
							if (!topic) {
								showError("Meet topic required", $message);
								return false;
							}

							var startTime;
							var endTime;
							if (startNow) {
								// quick meet for about 30min
								startTime = new Date();
								startTime.setMinutes(startTime.getMinutes() + 5);
								endTime = new Date();
								endTime.setMinutes(startTime.getMinutes() + 35);
							} else {
								startTime = startPicker.getDate();
								endTime = endPicker.getDate();
							}
							var participants = $form.find(".meetSpaceMembers select[name='meetParticipants']").val();
							if (!participants) {
								participants = [];
							}
							function addParticipant(user) {
								for (var pi = 0; pi < participants.length; pi++) {
									if (participants[pi] == user) {
										return false;
									}
								}
								participants.push(user);
							}

							var moxtraUsers = $form.find(".meetMoxtraContacts select[name='meetParticipants']").val();
							if (moxtraUsers) {
								// merge participants: remove duplicates
								for (var mi = 0; mi < moxtraUsers.length; mi++) {
									addParticipant(moxtraUsers[mi]);
								}
							}
							var emailUsers = $form.find(".meetEmailInvitees textarea[name='meetEmails']").val();
							if (emailUsers) {
								// merge participants: remove duplicates
								var emails = emailUsers.trim().split(/\s+/);
								for (var ei = 0; ei < emails.length; ei++) {
									addParticipant(emails[ei]);
								}
							}
							if (participants.length <= 0) {
								showError("At least one participant required", $message);
								return false;
							}

							// show creating meet pane
							$meetStart.hide();
							$meetSchedule.hide();
							$form.hide("fade");
							$creating.show("blind");

							function showMeet(meet) {
								// show created meet pane
								// meet link opens a page on Moxtra where user can start it
								//var $meetLink = $created.find(".meetLink > a");
								//$meetLink.attr("href", meet.startMeetUrl);
								//$meetLink.text(meet.startMeetUrl);

								//var $meetEvent = $created.find(".meetEvent > a");
								//var calendarLink = window.location.href;
								//var midx = calendarLink.lastIndexOf("/moxtra");
								//if (midx > 0) {
								//	calendarLink = calendarLink.slice(0, midx) + "/calendar";
								//}
								//$meetEvent.attr("href", calendarLink);

								if (startNow) {
									var $startButton = $created.find(".meetStartButton");
									$startButton.find("a").click(function() {
										// use Moxtra JS to open the meet immediately
										moxtra.moxtrajs().startMeet(meet.binderId, {
											end_meet : function(event) {
												log("end_meet: " + event);
											},
											save_meet : function(event) {
												//session_key, session_id, binder_id
												log("save_meet: " + event.session_key);
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

							var sameMeet = $meetPopup.data(topic);
							if (sameMeet && Math.abs(sameMeet.startTime.getTime() - startTime.getTime()) < 180000) {
								// use previously created meet
								showMeet(sameMeet);
							} else {
								var agenda = $form.find("textarea[name='meetAgenda']").val();
								var autoRec = $form.find("input[name='meetAutorec']").is(":checked");
								//var startTime = $form.find("input[name='meetStartTime']:visible").val();
								//var endTime = $form.find("input[name='meetEndTime']:visible").val();

								// XXX we cannot use REST service here as portal request required for creating calendar event
								//var newMeet = moxtra.createBinderMeet(spaceName, topic, agenda, startTime.getTime(), endTime.getTime(), autoRec,
								// participants);
								//var newMeet = moxtra.createMeet(topic, agenda, startTime.getTime(), endTime.getTime(), autoRec, participants);
								/*newMeet.done(function(meet) {
								$meetPopup.data(meet.name, meet);
								//$meetPopup.modal("hide");
								showMeet(meet);
								});
								newMeet.fail(function(e) {
								showError(e.message, $message);

								// get back to first pane, start button stays hidden
								//$meetStart.hide();
								//$meetSchedule.hide();
								$creating.hide("fade");
								$form.show("blind");
								});*/

								// XXX Juzu 0.6.2 doesn't work correctly with list/arrays params: concat participants into string
								$created.jzLoad("MoxtraBinderSpaceController.createMeet()", {
									spaceName : spaceName,
									name : topic,
									agenda : agenda,
									startTime : startTime.getTime(),
									endTime : endTime.getTime(),
									autoRecording : autoRec,
									users : participants.join()
								}, function(response) {
									var $msg = $created.find(".messageText");
									if ($msg.length > 0) {
										showError($msg, $message);

										// get back to first pane, start button stays hidden
										//$meetStart.hide();
										//$meetSchedule.hide();
										$creating.hide("fade");
										$form.show("blind");
									} else {
										var $meetInfo = $created.find(".meetInfo");
										var meet = {
											binderId : $meetInfo.data("meet-binderid"),
											sessionKey : $meetInfo.data("meet-sessionkey"),
											startLink : $meetInfo.data("meet-startlink"),
											startTime : new Date($meetInfo.data("meet-starttime")),
											endTime : new Date($meetInfo.data("meet-endtime"))
										};
										$meetPopup.data(topic, meet);
										showMeet(meet);
									}
								});
							}
						}

						// start/schedule button actions
						$startMenu.click(function() {
							// Start meet action
							hideError();
							hideError($message);
							resetMeet();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$meetStart.click(function(ev) {
										ev.preventDefault();
										hideError($message);
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
							hideError($message);
							resetMeet();
							// load current user contacts
							$form.find(".meetMoxtraContacts").jzLoad("MoxtraBinderSpaceController.contactsList()", function(response) {
								$form.find(".meetSpaceMembers").jzLoad("MoxtraBinderSpaceController.spaceMembersList()", function(response) {
									$meetSchedule.click(function(ev) {
										ev.preventDefault();
										hideError($message);
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
