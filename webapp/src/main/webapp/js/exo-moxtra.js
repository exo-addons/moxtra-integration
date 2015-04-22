/**
 * eXo Moxtra UI support.
 */
(function($, uiRightClickPopupMenu) {
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

	/**
	 * Read cookie.
	 */
	function getCookie(name, fromDocument) {
		var nameEQ = name + "=";
		var ca = ( fromDocument ? fromDocument : document).cookie.split(';');
		for (var i = 0; i < ca.length; i++) {
			var c = ca[i];
			while (c.charAt(0) == ' ') {
				c = c.substring(1, c.length);
			}
			if (c.indexOf(nameEQ) == 0) {
				var v = c.substring(nameEQ.length, c.length);
				// clean value from leading quotes (actual if set via eXo WS)
				return decodeURIComponent(v.match(/([^\"]+)/g));
			}
		}
		return null;
	}

	/**
	 * Add style to current document (to the end of head).
	 */
	function loadStyle(cssUrl) {
		if (document.createStyleSheet) {
			document.createStyleSheet(cssUrl);
			// IE way
		} else {
			if ($("head").find("link[href='" + cssUrl + "']").size() == 0) {
				var headElems = document.getElementsByTagName("head");
				var style = document.createElement("link");
				style.type = "text/css";
				style.rel = "stylesheet";
				style.href = cssUrl;
				headElems[headElems.length - 1].appendChild(style);
			} // else, already added
		}
	}

	/**
	 * Moxtra core class.
	 */
	function ExoMoxtra() {

		var currentUser = null;

		var authorized = false;

		var authLink;

		var initRequest = function(request) {
			var process = $.Deferred();

			// stuff in textStatus is less interesting: it can be "timeout",
			// "error", "abort", and "parsererror",
			// "success" or smth like that
			request.fail(function(jqXHR, textStatus, err) {
				if (jqXHR.status != 309) {
					// check if response isn't JSON
					var data;
					try {
						data = $.parseJSON(jqXHR.responseText);
						if ( typeof data == "string") {
							// not JSON
							data = jqXHR.responseText;
						}
					} catch(e) {
						// not JSON
						data = jqXHR.responseText;
					}
					// in err - textual portion of the HTTP status, such as "Not
					// Found" or "Internal Server Error."
					process.reject(data, jqXHR.status, err, jqXHR);
				}
			});
			// hacking jQuery for statusCode handling
			var jQueryStatusCode = request.statusCode;
			request.statusCode = function(map) {
				var user502 = map[502];
				if (!user502) {
					map[502] = function() {
						// treat 502 as request error also
						process.fail("Bad gateway", 502, "error");
					};
				}
				return jQueryStatusCode(map);
			};

			request.done(function(data, textStatus, jqXHR) {
				process.resolve(data, jqXHR.status, textStatus, jqXHR);
			});

			request.always(function(data, textStatus, errorThrown) {
				var status;
				if (data && data.status) {
					status = data.status;
				} else if (errorThrown && errorThrown.status) {
					status = errorThrown.status;
				} else {
					status = 200;
					// what else we could to do
				}
				process.always(status, textStatus);
			});

			// custom Promise target to provide an access to jqXHR object
			var processTarget = {
				request : request
			};
			return process.promise(processTarget);
		};

		var getMeet = function(sessionKey, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/meet/" + sessionKey,
				dataType : "json"
			});

			return initRequest(request);
		};

		var postMeet = function(name, agenda, startTime, endTime, autoRecording, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "POST",
				url : serverUrl + "/portal/rest/moxtra/meet",
				dataType : "json",
				data : {
					name : name,
					agenda : agenda,
					startTime : startTime,
					endTime : endTime,
					autoRecording : autoRecording ? true : false
				}
			});

			return initRequest(request);
		};

		var postMeetInviteUsers = function(sessionKey, usersEmails, message, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "POST",
				url : serverUrl + "/portal/rest/moxtra/meet/" + sessionKey + "/inviteusers",
				dataType : "json",
				data : {
					message : message,
					users : usersEmails
				}
			});

			return initRequest(request);
		};

		var getMoxtraUserAuth = function(async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/user/me",
				dataType : "json"
			});

			return initRequest(request);
		};

		var getExoUser = function(userName, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/user/exo/" + userName,
				dataType : "json"
			});

			return initRequest(request);
		};

		/**
		 * Open pop-up with given URL.
		 */
		var openWindow = function(url) {
			var w = 850;
			var h = 400;
			var left = (screen.width / 2) - (w / 2);
			var top = (screen.height / 2) - (h / 2);
			return window.open(url, 'Moxtra', 'width=' + w + ',height=' + h + ',top=' + top + ',left=' + left);
		};

		var waitAuth = function(authWindow) {
			var process = $.Deferred();
			var i = 0;
			var intervalId = setInterval(function() {
				var code = getCookie("moxtra-client-code");
				if (code == "authorized") {
					// user authorized
					intervalId = clearInterval(intervalId);
					process.resolve();
				} else {
					var error = getCookie("moxtra-client-error");
					if (error) {
						intervalId = clearInterval(intervalId);
						process.reject(error);
					} else if (authWindow && authWindow.closed) {
						intervalId = clearInterval(intervalId);
						log("Authentication canceled.");
						process.reject("Canceled");
					} else if (i > 310) {// ~5min
						// if open more 5min - close it and treat as not authenticated/allowed
						intervalId = clearInterval(intervalId);
						process.reject("Authentication timeout.");
					}
				}
				i++;
			}, 1000);
			return process.promise();
		};

		var authorize = function() {
			var process = $.Deferred();
			if (authorized) {
				process.resolve();
			} else {
				if (!authLink) {
					var userAuthProc = getMoxtraUserAuth();
					userAuthProc.done(function(authData) {
						if (authData.authorized) {
							authorized = true;
						} else {
							authorized = false;
							authLink = authData.authLink;
						}
					});
					userAuthProc.fail(function(message) {
						process.reject("Cannot get authorization link. " + message);
					});
				}
				if (authorized) {
					process.resolve();
				} else {
					if (authLink) {
						// open Moxtra OAuth2 login
						var authWindow = openWindow(authLink);
						// wait for authentication
						var auth = waitAuth(authWindow);
						auth.done(function() {
							log("INFO: " + currentUser + " user authenticated successfully.");
							authorized = true;
							process.resolve();
						});
						auth.fail(function(message) {
							if (message) {
								log("ERROR: " + currentUser + " authorization error: " + message);
							}
							process.reject(message);
						});
					} else {
						process.reject("Authorization link not found.");
					}
				}
			}
			return process.promise();
		};

		/**
		 * Create a meet with given parameters.
		 */
		var createMeet = function($button, name, agenda, startTime, endTime, autoRecording, users) {
			//if (!$button.data("moxtra-meet-creating")) {
			//var cursorCss = $button.css("cursor");
			//$button.css("cursor", "wait");
			//$button.data("moxtra-meet-creating", true);
			//try {
			var createProc = postMeet(name, agenda, startTime, endTime, autoRecording);
			createProc.done(function(meet) {
				if (meet) {
					// open meet in new window: add a href to the button and click it again
					// $button.attr("href", meet.startMeetUrl);
					// $button.addClass("meetReady");
					// $button.click();

					// then add users to the meet asynchronously
					var inviteProc = postMeetInviteUsers(meet.sessionKey, users, false);
					inviteProc.done(function(meet) {
						// TODO do we have something to do here? Show a notice to an user that meet created?
					});
					inviteProc.fail(function(error) {
						// TODO notif user about an error
						log("ERROR: Error inviting users to meet " + name + ". " + (error.message ? error.message : error), error);
					});
				} else {
					log("ERROR: Empty object returned for meet " + name);
				}
			});
			createProc.fail(function(error) {
				// TODO notif user about an error
				log("ERROR: Error creating meet " + name + ". " + (error.message ? error.message : error), error);
			});
			return createProc;
			//} finally {
			//$button.data("moxtra-meet-creating", false);
			//	$button.css("cursor", cursorCss);
			//}
			//}
		};

		this.initUser = function(userName, isAuthorized, authLinkUrl) {
			currentUser = userName;
			authorized = isAuthorized ? isAuthorized : false;

			// init Auth link and button
			if (authLinkUrl) {
				authLink = authLinkUrl;
			}

			// page may contain auth button, init it accordingly
			var $authButton = $("a.moxtraAuthLink");
			if ($authButton.size() > 0) {
				var url = $authButton.attr("href");
				if (url) {
					if (!authLink) {
						authLink = url;
					}
					$authButton.removeAttr("href");
					// $authButton.attr("href", "javascript:void(0);");
					$authButton.click(function() {
						var auth = authorize();
						auth.fail(function(error) {
							alert(error);
						});
					});
				}
			}
		};

		this.initCalendar = function() {
			// if user not authorized, we force auth login on enable checkbox click immediately,
			// then we'll proceed the enabler action.
			var $enableMeet = $("input#enableMoxtraMeet");
			$enableMeet.change(function(elem) {
				if (!$enableMeet.data("moxtra-meet-enabling")) {
					if ($enableMeet.is(":checked")) {
						if (!authorized) {
							elem.preventDefault();
							$enableMeet.attr('checked', false);
							// uncheck
							// first authorize user
							try {
								// temp marker to avoid double invocation
								$enableMeet.data("moxtra-meet-enabling", true);
								var auth = authorize();
								auth.done(function() {
									$enableMeet.click();
								});
								auth.fail(function(error) {
									log("Moxtra authorization error " + error);
									// TODO notify the error to an user
								});
								auth.always(function() {
									$enableMeet.data("moxtra-meet-enabling", false);
								});
							} catch(e) {
								log("Error enabling Moxtra Meet", e);
							}
						}
					}
				}
			});
		};

		/**
		 * Init meet button in current page (logic based on eXo Chat/Weemo scripts).
		 */
		this.initMeetButton = function(compId) {
			var $tiptip = $("#tiptip_content");
			if ($tiptip.size() == 0 || $tiptip.hasClass("DisabledEvent")) {
				setTimeout($.proxy(this.initMeetButton, this), 250, compId);
				return;
			}

			// had classes uiIconWeemoVideoCalls uiIconWeemoLightGray
			var meetLabel = "<i class='uiIconMoxtra'></i>Meet";

			if (!compId) {
				// by default we work with whole page
				compId = "UIWorkingWorkspace";
			}

			// attachWeemoToPopups (to tiptip_content elem)
			$("#" + compId).find('a:[href*="/profile/"]').each(function() {
				// attach action to
				$(this).mouseenter(function() {
					// need wait for popover initialization
					setTimeout(function() {
						// Find user's first name for a tip
						var $td = $tiptip.children("#tipName").children("tbody").children("tr").children("td");
						if ($td.size() > 1) {
							var $userAction = $tiptip.find(".uiAction");
							if ($userAction.size() > 0 && $userAction.find("a.meetStartAction").size() === 0) {
								var $userTitle = $("a", $td.get(1));
								var userTitle = $userTitle.text();
								var meetButton = "<a type='button' class='btn meetStartAction moxtraIcon' title='Start meeting with " + userTitle + "' target='_blank'";
								meetButton += " style='margin-left:5px;'>";
								meetButton += meetLabel;
								meetButton += "</a>";
								$userAction.append(meetButton);
								var $button = $userAction.find("a.meetStartAction");
								$button.click(function(e) {
									e.preventDefault();
									if (authorized) {
										var href = $button.attr("href");
										if (href) {
											log("meetStartAction:clicked " + href);
											window.open(href);
										} else {
											// Find username in eXo, get user email and start a meet with current and this users
											var userName = $userTitle.attr("href");
											var userName = userName.substring(userName.lastIndexOf("/") + 1, userName.length);
											log("meetStartAction:clicked " + userName);
											var exoUserProc = getExoUser(userName, false);
											exoUserProc.done(function(user) {
												var startTime = new Date();
												startTime.setMinutes(startTime.getMinutes() + 1);
												var endTime = new Date();
												// personal talk for about 30min
												endTime.setMinutes(endTime.getMinutes() + 30);
												// mark cursor loading
												var cursorCss = $button.css("cursor");
												$button.css("cursor", "wait");
												var meetName = "Meet with " + userTitle;
												var meetWindow = window.open("", "_blank");
												try {
													var proc = createMeet($button, meetName, "", startTime.getTime(), endTime.getTime(), false, [user.email]);
													//var proc = postMeet("Test", "", startTime.getTime(), endTime.getTime());
													//window.open("https://www.moxtra.com/398165623");
													//meetWindow.location.href = JSON.parse(proc.request.responseText).startMeetUrl;
													proc.done(function(meet) {
														// open meet in new window: add a href to the button and click it again
														$button.attr("href", meet.startMeetUrl);
														$button.addClass("meetReady");
														meetWindow.location.href = meet.startMeetUrl;
													});
													proc.fail(function() {
														meetWindow.close();
													});
												} catch(e) {
													meetWindow.close();
													log("Error creating meet " + meetName + " " + error, error);
												} finally {
													$button.css("cursor", cursorCss);
												}
											});
											exoUserProc.fail(function(error) {
												log("Error reading eXo user " + userName + " " + error, error);
												// TODO notify the error to an user
											});
										}
									} else {
										log("meetStartAction:clicked authorize");
										var authProc = authorize();
										authProc.done(function() {
											$button.click();
										});
										authProc.fail(function(error) {
											// TODO notif user
											log("ERROR: Error authorizing user " + ( currentUser ? currentUser : ""), error);
										});
									}
								});
							}
						}
					}, 750);
				});
			});

			// attachWeemoToProfile

			// attachWeemoToConnections
		};
	}

	// init server URL
	var location = window.location;
	var hostName = location.hostname;
	if (location.port) {
		hostName += ":" + location.port;
	}
	var serverUrl = location.protocol + "//" + hostName;

	var client = new ExoMoxtra();

	// Load Moxtra dependencies only in top window (not in iframes of gadgets).
	if (window == top) {
		try {
			// load required styles
			loadStyle("/moxtra/skin/jquery-ui.css");
			loadStyle("/moxtra/skin/jquery.pnotify.default.css");
			loadStyle("/moxtra/skin/jquery.pnotify.default.icons.css");
			loadStyle("/moxtra/skin/exo-moxtra.css");

			// configure Pnotify
			// use jQuery UI css
			$.pnotify.defaults.styling = "jqueryui";
			// no history roller in the right corner
			$.pnotify.defaults.history = false;

			// init Meet button
			client.initMeetButton();
		} catch(e) {
			log("Error configuring Moxtra styles.", e);
		}
	}

	return client;
})($, uiRightClickPopupMenu);
