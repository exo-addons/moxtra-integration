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
	 * */
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

		/**
		 * NOT USED.
		 */
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

		/**
		 * NOT USED.
		 */
		var getMeet = function(userId, eventTitle, fromDate, toDate, calendarId) {
			var request = $.ajax({
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/meet",
				dataType : "json",
				data : {
					userId : userId,
					eventTitle : eventTitle,
					fromDate : fromDate,
					toDate : toDate,
					calendarId : calendarId
				}
			});

			return initRequest(request);
		};

		/**
		 * NOT USED.
		 */
		var enableMeet = function(userId, eventTitle, fromDate, toDate, calendarId) {
			var request = $.ajax({
				type : "POST",
				url : serverUrl + "/portal/rest/moxtra/meet",
				dataType : "json",
				data : {
					userId : userId,
					eventTitle : eventTitle,
					fromDate : fromDate,
					toDate : toDate,
					calendarId : calendarId
				}
			});

			return initRequest(request);
		};

		/**
		 * NOT USED.
		 */
		var disableMeet = function(userId, eventTitle, fromDate, toDate, calendarId) {
			var request = $.ajax({
				type : "DELETE",
				url : serverUrl + "/portal/rest/moxtra/meet",
				dataType : "json",
				data : {
					userId : userId,
					eventTitle : eventTitle,
					fromDate : fromDate,
					toDate : toDate,
					calendarId : calendarId
				}
			});

			return initRequest(request);
		};

		/**
		 * NOT USED.
		 */
		var inviteMeetUsers = function(userId, eventTitle, fromDate, toDate, calendarId) {
			var request = $.ajax({
				type : "POST",
				url : serverUrl + "/portal/rest/moxtra/meet/invite",
				dataType : "json",
				data : {
					userId : userId,
					eventTitle : eventTitle,
					fromDate : fromDate,
					toDate : toDate,
					calendarId : calendarId
				}
			});

			return initRequest(request);
		};

		// ******************************************************************************************
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
					process.reject("Cannot find authorization link.");
				}
			}
			return process.promise();
		};

		this.initUser = function(userName, isAuthorized) {
			currentUser = userName;
			authorized = isAuthorized ? isAuthorized : false;

			// init Auth link and button
			var $authButton = $("a.moxtraAuthLink");
			if ($authButton.size() > 0) {
				var url = $authButton.attr("href");
				if (url) {
					authLink = url;
					$authButton.removeAttr("href");
					//$authButton.attr("href", "javascript:void(0);");
					$authButton.click(function() {
						var auth = authorize();
						auth.fail(function(error) {
							alert(error);
						});
					});
				}
			}

			// init UI
			var $enableMeet = $("input#enableMoxtraMeet, input#UIEnableMoxtraCheckBoxInput");
			// TODO avoid checking for undefined by loading only required part of the tab
			if ($enableMeet.size() > 0 && typeof $enableMeet.data("moxtra-meet-enabled") === "undefined") {
				$enableMeet.change(function() {
					if (!$enableMeet.data("moxtra-meet-enabling")) {
						if ($enableMeet.is(":checked")) {
							var $enableAction = $("a.enableMoxtraMeetAction");
							try {
								// TODO better UX for not authorized user
								//if ($enableAction.size() > 0) {
								if (!$enableMeet.data("moxtra-meet-enabled")) {
									var auth = authorize();
									auth.done(function() {
										$enableMeet.data("moxtra-meet-enabling", true);
										try {
											if ($enableAction.size() > 0) {
												$enableAction.click();
											} else if ($enableMeet.data("moxtra-meet-proceed")) {
												$enableMeet.data("moxtra-meet-proceed", false);
												$enableMeet.click();
											}
										} finally {
											$enableMeet.data("moxtra-meet-enabling", false);
										}
										$enableMeet.data("moxtra-meet-enabled", true);
									});
									auth.fail(function(error) {
										// TODO notify the error to an user
										// uncheck
										$enableMeet.attr('checked', false);
									});
								}
								//} else {
								//	// uncheck
								//	$enableMeet.attr('checked', false);
								//}
							} catch(e) {
								log("Error enabling Moxtra Meet", e);
								// uncheck
								$enableMeet.attr('checked', false);
							}
						} else {
							$("a.disableMoxtraMeetAction").click();
							$enableMeet.data("moxtra-meet-enabled", false);
						}
					}
				});
				$("input#UIEnableMoxtraCheckBoxInput").click(function(e) {
					// disable onlick in quick-add form if not authorized
					if (!authorized) {
						$(e).data("moxtra-meet-proceed", true);
						$(e).change();
						e.preventDefault();
						return false;
					}
				});
			}

			$("input#meetAutorecording").change(function() {
				if ($(this).is(":checked")) {
					$("a.enableMeetAutorecordingAction").click();
				} else {
					$("a.disableMeetAutorecordingAction").click();
				}
			});
		};

		// deprecated
		this.authUserEnableMeet = function(url) {
			authLink = url;
			var auth = authorize();
			auth.done(function() {
				$("input#UIEnableMoxtraCheckBoxInput").change();
				// or click?
			});
			auth.fail(function(error) {
				alert(error);
			});
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
		} catch(e) {
			log("Error configuring Moxtra styles.", e);
		}
	}

	return client;
})($, uiRightClickPopupMenu);

