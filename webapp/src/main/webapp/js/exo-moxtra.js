/**
 * eXo Moxtra UI support.
 */
(function($) {
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
	 * Add script to current document (to the end of head).
	 */
	function loadScript(jsUrl, jsId) {
		var process = $.Deferred();
		if ($("head").find("script[src='" + jsUrl + "']").size() == 0) {
			$.get(jsUrl, undefined, function(data) {
				var script = document.createElement("script");
				script.type = "text/javascript";
				script.src = jsUrl;
				script.setAttribute("src", jsUrl);
				script.text = data;
				script.id = jsId;

				document.head.appendChild(script);
				//var headElems = document.getElementsByTagName("head");
				//headElems[headElems.length - 1].appendChild(script);
			}, "script");
		}
		if (jsId) {
			function jsReady() {
				var $js = $("head").find("#" + jsId);
				return $js.size() > 0;
			}

			var attempts = 40;
			// abt 10sec.
			function waitReady() {
				attempts--;
				if (attempts >= 0) {
					setTimeout(function() {
						if (jsReady()) {
							process.resolve();
						} else {
							waitReady();
						}
					}, 550);
				} else {
					process.reject("Script load timeout " + jsUrl);
				}
			}

			if (jsReady()) {
				process.resolve();
			} else {
				waitReady();
			}
		} else {
			process.resolve();
		}
		return process.promise();
	}

	/**
	 * Moxtra integration class.
	 */
	function ExoMoxtra() {

		var currentUser = null;

		var authorized = false;

		var authLink = null;

		var pageWindow;

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
						process.reject("Bad gateway", 502, "error");
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

		var findBinderPage = function(spaceName, pageNodeUUID, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/binder/space/" + spaceName + "/page/" + pageNodeUUID,
				dataType : "json"
			});

			return initRequest(request);
		};

		var postBinderSpaceMeet = function(spaceName, name, agenda, startTime, endTime, autoRecording, users, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "POST",
				url : serverUrl + "/portal/rest/moxtra/binder/space/" + spaceName + "/meets",
				dataType : "json",
				data : {
					name : name,
					agenda : agenda,
					startTime : startTime,
					endTime : endTime,
					autoRecording : autoRecording ? true : false,
					users : users
				}
			});

			return initRequest(request);
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

		var findInviteeMeet = function(inviteeEmail, async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/meet/find?invitee=" + encodeURIComponent(inviteeEmail),
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

		var getAccessToken = function(async) {
			var request = $.ajax({
				async : async ? true : false,
				type : "GET",
				url : serverUrl + "/portal/rest/moxtra/login/accesstoken",
				dataType : "json"
			});

			return initRequest(request);
		};

		var moxtrajs = new MoxtraJS(getAccessToken);

		/**
		 * Open pop-up with given URL.
		 */
		var openPopup = function(url) {
			log("openPopup: " + url);
			var w = 700;
			var h = 400;
			var left = (screen.width / 2) - (w / 2);
			var top = (screen.height / 2) - (h / 2);
			return window.open(url, "_blank", "width=" + w + ",height=" + h + ",top=" + top + ",left=" + left);
		};

		var waitAuth = function(authWindow) {
			var process = $.Deferred();
			if (authWindow) {
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
							process.reject("Canceled");
						} else if (i > 310) {// ~5min
							// if open more 5min - close it and treat as not authenticated/allowed
							intervalId = clearInterval(intervalId);
							process.reject("Authorization timeout.");
						}
					}
					i++;
				}, 1000);
			} else {
				process.reject("Authorization window not found.");
			}
			return process.promise();
		};

		var authorize = function(userWindow) {
			var process = $.Deferred();
			if (authorized) {
				process.resolve();
			} else {
				var authWindow;

				function openAuthWindow() {
					if (userWindow) {
						// using provided window
						userWindow.location.href = authLink;
						authWindow = userWindow;
					} else {
						// open a new window
						authWindow = openPopup(authLink);
					}
				}

				function closeAuthWindow() {
					if (authWindow && authWindow != userWindow) {
						authWindow.close();
					}
				}

				if (authLink) {
					// open Moxtra OAuth2 login
					//authWindow = openPopup(authLink);
					openAuthWindow();
				} else {
					//authWindow = openPopup("");
					var userAuthProc = getMoxtraUserAuth(false);
					userAuthProc.done(function(authData) {
						if (authData.authorized) {
							authorized = true;
							closeAuthWindow();
							currentUser = authData.userName;
							process.resolve(authData);
						} else {
							authorized = false;
							authLink = authData.authLink;
						}
					});
					userAuthProc.fail(function(message) {
						if (authWindow) {
							authWindow.close();
						}
						process.reject("Cannot get authorization link. " + message);
					});
				}
				if (!authorized && authLink) {
					//authWindow.location.href = authLink;
					if (!authWindow) {
						//authWindow = openPopup(authLink);
						openAuthWindow();
					}
					// wait for authentication
					var auth = waitAuth(authWindow);
					auth.done(function() {
						log("INFO: " + currentUser + " user authenticated successfully.");
						authorized = true;
						closeAuthWindow();
						process.resolve();
					});
					auth.fail(function(message) {
						if (message) {
							log("ERROR: " + currentUser + " authorization error: " + message);
						}
						process.reject(message);
					});
				} else {
					if (process.state() === "pending") {
						process.reject("Authorization link not found.");
					}
				}
			}
			return process.promise();
		};

		/**
		 * Create a meet with given parameters.
		 */
		var createMeet = function(name, agenda, startTime, endTime, autoRecording, users) {
			var create = postMeet(name, agenda, startTime, endTime, autoRecording, true);
			create.done(function(meet) {
				if (meet) {
					// then add users to the meet asynchronously
					var invite = postMeetInviteUsers(meet.sessionKey, users, true);
					invite.done(function(meet) {
						// TODO do we have something to do here? Show a notice to an user that meet created?
					});
					invite.fail(function(error) {
						// TODO notif user about an error
						log("ERROR: Error inviting users to meet " + name + ". " + (error.message ? error.message : error), error);
					});
				} else {
					log("ERROR: Empty object returned for meet " + name);
				}
			});
			create.fail(function(error) {
				// TODO notif user about an error
				log("ERROR: Error creating meet " + name + ". " + (error.message ? error.message : error), error);
			});
			return create;
		};

		/**
		 * Init binder space page in target window.
		 */
		var initPageWindow = function(target, binderId, pageId, spaceName, pageNodeUUID) {
			var process = $.Deferred();
			var $t = $(target.document);
			// ensure user authorized if runs in this window or page not exists in Moxtra
			if (authorized || (target != window && binderId && pageId)) {
				var $editor = $t.find("#moxtra-page-editor");
				var $progress = $t.find("#moxtra-page-progress");
				if (!binderId) {
					binderId = $editor.data("binder-id");
				}
				if (binderId) {
					if (!pageId) {
						pageId = $editor.data("binder-page-id");
					}

					function showPage() {
						var callbacks = {
							start_page : function(event) {
								$progress.hide();
								$editor.show();
							},
							publish_feed : function(event) {
								log("publish_feed: " + JSON.stringify(event));
							},
							receive_feed : function(event) {
								log("receive_feed: " + JSON.stringify(event));
							}
						};
						if (target === window) {
							var page = moxtrajs.showPages(binderId, pageId, "moxtra-page-editor", callbacks);
							page.done(function() {
								process.resolve();
							});
							page.fail(function(m, e) {
								process.reject(m, e);
							});
						} else {
							try {
								// target window should care about authorization by itself
								$t.ready(function() {
									target.initPage(binderId, pageId);
									// when opening in another window it's resolved for this current one
									process.resolve();
								});
							} catch(e) {
								process.reject(e, e);
							}
						}
					}

					if (pageId) {
						showPage();
					} else {
						if (!spaceName) {
							spaceName = $editor.data("binder-space-name");
						}
						if (!pageNodeUUID) {
							pageNodeUUID = $editor.data("binder-page-node-uuid");
						}
						if (spaceName && pageNodeUUID) {
							var findAttempts = 20;
							function findShowPage() {
								findAttempts--;
								var pageFind = findBinderPage(spaceName, pageNodeUUID, true);
								pageFind.done(function(page) {
									pageId = page.id;
									showPage();
								});
								pageFind.fail(function(data, status) {
									if (status == 404 && data.code == "page_not_found") {
										// page not yet created - need wait it
										log("WARN: page not found in " + binderId + ", attempt: " + findAttempts);
										if (findAttempts >= 0) {
											setTimeout(function() {
												findShowPage();
											}, 2500);
										} else {
											$progress.hide();
											$t.find("#moxtra-page-notopen").show();
										}
									} else {
										process.reject(data);
									}
								});
							}

							findShowPage();
						} else {
							process.reject("A spaceName and pageNodeUUID required for initPage()");
						}
					}
				} else {
					process.reject("Moxtra binderId required for initPage()");
				}
			} else {
				// temp marker to avoid double invocation during auth
				var opening = $t.data("moxtra-page-opening");
				if (opening) {
					return opening;
				} else {
					try {
						$t.data("moxtra-page-opening", process);
						var auth = authorize();
						auth.done(function() {
							var page = initPageWindow(target, binderId, pageId, spaceName, pageNodeUUID);
							page.done(function() {
								process.resolve();
							});
							page.fail(function(m, e) {
								process.reject(m, e);
							});
						});
						auth.fail(function(error) {
							process.reject("Moxtra authorization error: " + error);
						});
						auth.always(function() {
							$t.removeData("moxtra-page-opening");
						});
					} catch(e) {
						process.reject("Error opening Moxtra page " + e, e);
					}
				}
			}
			return process.promise();
		};

		this.moxtrajs = function(moxtraApi) {
			if (moxtraApi) {
				moxtrajs = new MoxtraJS(getAccessToken, moxtraApi);
			}
			return moxtrajs;
		};

		this.isAuthorized = function() {
			if (authorized) {
				return true;
			} else {
				var userAuthProc = getMoxtraUserAuth(false);
				userAuthProc.done(function(authData) {
					if (authData.authorized) {
						authorized = true;
						currentUser = authData.userName;
					} else {
						authorized = false;
						authLink = authData.authLink;
					}
				});
				userAuthProc.fail(function() {
					authorized = false;
				});
				return authorized;
			}
		};

		this.authorize = function() {
			// public access to authorization logic, but initUser() should be called first
			return authorize();
		};

		this.initUser = function(userName, isAuthorized, authLinkUrl) {
			currentUser = userName;
			authorized = isAuthorized ? isAuthorized : false;

			// init Auth link and button
			if (authLinkUrl) {
				authLink = authLinkUrl;
			}

			// page may contain auth button, init it accordingly
			// var $authButton = $("a.moxtraAuthLink");
			// if ($authButton.size() > 0) {
			// var url = $authButton.attr("href");
			// if (url) {
			// if (!authLink) {
			// authLink = url;
			// }
			// $authButton.removeAttr("href");
			// // $authButton.attr("href", "javascript:void(0);");
			// $authButton.click(function() {
			// authorize();
			// });
			// }
			// }
		};

		this.initCalendar = function() {
			var $meetTab = $("#UIEmeetingTab");

			// init auth button for Calendar UI
			var $authButton = $meetTab.find("a.moxtraAuthLink");
			if ($authButton.size() > 0) {
				var url = $authButton.attr("href");
				if (url) {
					if (!authLink) {
						authLink = url;
					}
					$authButton.removeAttr("href");
					// $authButton.attr("href", "javascript:void(0);");
					$authButton.click(function() {
						authorize();
					});
				}
			}

			// if user not authorized, we force auth login on enable checkbox click immediately,
			// then we'll proceed the enabler action.
			var $enableMeet = $meetTab.find("input#enableMeet");
			$enableMeet.change(function(ev) {
				if (!$enableMeet.data("moxtra-meet-enabling")) {
					if ($enableMeet.is(":checked")) {
						if (!authorized) {
							ev.preventDefault();
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

			// meet start button using MoxtraJS
			var $startButton = $meetTab.find(".meetStartAction");
			if ($startButton.size() > 0) {
				//$startButton.attr("href", "javascript:void(0);");
				var binderId = $startButton.data("meet-binderid");
				var sessionKey = $startButton.data("meet-sessionkey");
				var join = $startButton.data("meet-join");
				if (binderId || (sessionKey && join)) {
					var moxtraReady = moxtrajs.preload();
					moxtraReady.done(function() {
						$startButton.find("a").click(function(ev) {
							ev.preventDefault();
							if (join) {
								moxtrajs.joinMeet(sessionKey, {
									error : function(event) {
										// TODO show in event form?
									}
								});
							} else {
								moxtrajs.startMeet(binderId, {
									end_meet : function(event) {
										// TODO show in event form?
									},
									save_meet : function(event) {
										// TODO show in event form?
									},
									error : function(event) {
										// TODO show in event form?
									}
								});
							}
							return false;
						});
					});
					moxtraReady.fail(function(e) {
						log("Error preloading MoxtraJS " + e, e);
					});
				}
			}
		};

		/**
		 * Init meet button in current page (logic based on eXo Chat/Weemo scripts).
		 */
		this.initMeetButton = function(compId) {
			var $tiptip = $("#tiptip_content");
			// if not in user profile wait for UIUserProfilePopup script load
			if (window.location.href.indexOf("/portal/intranet/profile") < 0) {
				if ($tiptip.size() == 0 || $tiptip.hasClass("DisabledEvent")) {
					setTimeout($.proxy(this.initMeetButton, this), 250, compId);
					return;
				}
			}

			// had classes uiIconWeemoVideoCalls uiIconWeemoLightGray
			var meetLabel = "<i class='uiIconMoxtra'></i>Meet";

			if (!compId) {
				// by default we work with whole page
				compId = "UIWorkingWorkspace";
			}

			function addButton($userAction, userName, userTitle, pullRight) {
				if ($userAction.size() > 0 && $userAction.find("a.meetStartAction").size() === 0) {
					var meetName = "Meeting with " + userTitle;
					// check if meet wasn't already started for this user in this page
					// TODO use dedicated elem for such caching on client
					var meetReady = $("a.meetStartAction.meetReady[data-moxtra-meet-invitee='" + userName + "']");
					var meetButton = "<a type='button' class='btn meetStartAction moxtraIcon";
					if (pullRight) {
						meetButton += " pull-right";
					}
					if (meetReady.size() > 0) {
						meetButton += " meetReady";
					}
					meetButton += "' title='Start meeting with " + userTitle + "' target='_blank' style='margin-left:5px;'";
					if (meetReady.size() > 0) {
						meetButton += " href='" + meetReady.attr("href") + "'";
					}
					meetButton += ">";
					meetButton += meetLabel;
					meetButton += "</a>";
					if (pullRight) {
						$userAction.prepend(meetButton);
					} else {
						$userAction.append(meetButton);
					}
					var $button = $userAction.find("a.meetStartAction");
					var meetWindow;
					$button.click(function(e) {
						e.preventDefault();
						var href = $button.attr("href");
						if (href) {
							window.open(href, "_blank");
						} else {
							if (authorized) {
								if (!meetWindow) {
									// Prepare new windpw for future meet in user event thread
									meetWindow = window.open("", "_blank");
									meetWindow.document.write("<div style='cursor:wait; height: 200px; vertical-align: middle; margin-right: auto; margin-left: auto; width: 800px; text-align: center;'>Wait, " + meetName + " is opening...</div>");
								}
								var user = getExoUser(userName, true);
								user.done(function(user) {
									var uinfo = user.email + "+" + user.userName;
									// personal talk for about 30min
									var startTime = new Date();
									startTime.setMinutes(startTime.getMinutes() + 1);
									var endTime = new Date();
									endTime.setMinutes(startTime.getMinutes() + 30);
									// mark cursor loading
									var cursorCss = $button.css("cursor");
									$button.css("cursor", "wait");
									try {
										function showMeet(meet) {
											$button.attr("href", meet.startMeetUrl);
											$button.addClass("meetReady");
											$button.attr("title", "Open meeting with " + userTitle);
											$button.attr("data-moxtra-meet-invitee", userName);
											meetWindow.location.href = meet.startMeetUrl;
										}

										function createNewMeet() {
											var create = createMeet(meetName, "", startTime.getTime(), endTime.getTime(), false, [uinfo]);
											create.done(function(meet) {
												showMeet(meet);
											});
											create.fail(function() {
												meetWindow.close();
											});
										}

										var search = findInviteeMeet(uinfo, true);
										search.done(function(meet) {
											if (meet) {
												// meet already exists
												showMeet(meet);
											} else {
												// create a meet
												createNewMeet();
											}
										});
										search.fail(function() {
											// create a meet if search failed
											createNewMeet();
										});
									} catch(e) {
										meetWindow.close();
										log("Error creating meet " + meetName + " " + error, error);
									} finally {
										$button.css("cursor", cursorCss);
									}
								});
								user.fail(function(error) {
									log("Error reading eXo user " + userName + " " + error, error);
									// TODO notify the error to an user
								});
							} else {
								// we can use meetWindow as auth window, it will not be closed on done
								var auth = authorize(meetWindow);
								auth.done(function() {
									$button.click();
								});
								auth.fail(function(error) {
									// TODO notif user
									log("ERROR: Error authorizing user " + ( currentUser ? currentUser : ""), error);
								});
							}
						}
					});
				}
			}

			function extractUserName($userLink) {
				var userName = $userLink.attr("href");
				return userName.substring(userName.lastIndexOf("/") + 1, userName.length);
			}

			// user popovers
			// XXX hardcoded for peopleSuggest as no way found to add MoxtraLifecucle to its portlet (juzu)
			$("#" + compId + ", #peopleSuggest").find('a:[href*="/profile/"]').each(function() {
				// attach action to
				$(this).mouseenter(function() {
					// need wait for popover initialization
					setTimeout(function() {
						// Find user's first name for a tip
						var $td = $tiptip.children("#tipName").children("tbody").children("tr").children("td");
						if ($td.size() > 1) {
							var $userLink = $("a", $td.get(1));
							var userTitle = $userLink.text();
							var userName = extractUserName($userLink);
							var $userAction = $tiptip.find(".uiAction");
							addButton($userAction, userName, userTitle);
						}
					}, 600);
				});
			});

			// user panel in connections (all, personal and in space)
			$("#" + compId).find(".spaceBox").each(function(i, elem) {
				var $userLink = $(elem).find(".spaceTitle a:first");
				if ($userLink.size() > 0) {
					var userTitle = $userLink.text();
					var userName = extractUserName($userLink);
					var $userAction = $(elem).find(".connectionBtn");
					addButton($userAction, userName, userTitle, true);
				}
			});

			// single user profile
			$("#" + compId).find("#UIProfile").each(function(i, elem) {
				var $userName = $(elem).find("#UIBasicInfoSection label[for='username']");
				var userName = $.trim($userName.siblings().text());
				var $firstName = $(elem).find("#UIBasicInfoSection label[for='firstName']");
				var $lastName = $(elem).find("#UIBasicInfoSection label[for='lastName']");
				var userTitle = $.trim($firstName.siblings().text()) + " " + $.trim($lastName.siblings().text());
				var $userAction = $(elem).find("#UIHeaderSection h3");
				addButton($userAction, userName, userTitle);
			});
		};

		/**
		 * Open new window for a page.
		 */
		this.openPage = function(binderId, pageId, spaceName, pageNodeUUID) {
			var process = $.Deferred();
			if (pageWindow) {
				$(pageWindow).ready(function() {
					var page = initPageWindow(pageWindow, binderId, pageId, spaceName, pageNodeUUID);
					page.done(function() {
						pageWindow.focus();
						process.resolve(pageWindow.href);
						pageWindow = null;
					});
					page.fail(function(message, e) {
						log("ERROR: " + message, e);
						// TODO notify the error to an user
						process.reject(message, e);
					});
				});
			} else {
				log("ERROR: page window not found");
				process.reject("Page window not found");
			}
			return process.promise();
		};

		/**
		 * Init binder space page in current page.
		 */
		this.initPage = function(binderId, pageId, spaceName, pageNodeUUID) {
			var page = initPageWindow(window, binderId, pageId, spaceName, pageNodeUUID);
			page.fail(function(message, e) {
				log("ERROR: " + message, e);
				// TODO notify the error to an user
			});
			return page;
		};

		/**
		 * Public access to meet creation.
		 */
		this.createMeet = function(name, agenda, startTime, endTime, autoRecording, users) {
			return createMeet(name, agenda, startTime, endTime, autoRecording, users);
		};

		/**
		 * Create a meet in binder space with given parameters.
		 */
		this.createBinderMeet = function(spaceName, name, agenda, startTime, endTime, autoRecording, users) {
			var create = postBinderSpaceMeet(spaceName, name, agenda, startTime, endTime, autoRecording, users, true);
			create.done(function(meet) {
				// TODO nothing here?
			});
			create.fail(function(error) {
				// TODO notif user about an error
				log("ERROR: Error creating binder meet " + name + ". " + (error.message ? error.message : error), error);
			});
			return create;
		};

		/**
		 * Init binder space documents app.
		 */
		this.initDocuments = function(openInNewWindow) {
			// ensure authorization
			var $editInMoxtra = $("#ECMContextMenu a[exo\\:attr='EditInMoxtra']");
			// TODO cleanup
			// if (openInNewWindow) {
			// $editInMoxtra.click(function() {
			// // open blank window before ajax request
			// var url = serverUrl + "/portal/rest/moxtra/page/";
			// if (pageWindow) {
			// if (!pageWindow.location.host || pageWindow.location.href === url) {
			// pageWindow.close();
			// }
			// }
			// pageWindow = window.open(url, "_blank");
			// window.focus();
			// });
			// }
			// if (!authorized) {
			// // remove original action href to avoid calling the service under not authr user
			// $editInMoxtra.each(function(i, elem) {
			// var action = $(elem).attr("href");
			// //var jsi = action.indexOf("javascript:");
			// //if (jsi == 0) {
			// //	action = action.slice(11);
			// //}
			// $(elem).data("moxtra-page-action", action);
			// $(elem).removeAttr("href");
			// });
			// }

			var lastWindow;

			function openPageWindow() {
				var url = serverUrl + "/portal/rest/moxtra/page/";
				if (lastWindow) {
					lastWindow.location = url;
					pageWindow = lastWindow;
				} else {
					if (pageWindow) {
						if (!pageWindow.location || pageWindow.location.host || pageWindow.location.href === url) {
							pageWindow.close();
						}
					}
					pageWindow = window.open(url, "_blank");
				}
				pageWindow.focus();
				return pageWindow;
			}


			$editInMoxtra.click(function(ev) {
				if (!$editInMoxtra.data("moxtra-page-opening")) {
					// open blank window before ajax request
					if (!lastWindow && openInNewWindow) {
						lastWindow = openPageWindow();
					}
					if (authorized) {
						lastWindow = null;
						return true;
					} else {
						ev.preventDefault();
						// first authorize user
						try {
							// temp marker to avoid double invocation
							$editInMoxtra.data("moxtra-page-opening", true);
							var auth = authorize(pageWindow);
							auth.done(function() {
								$editInMoxtra.data("moxtra-page-opening", false);
								$editInMoxtra.click();
							});
							auth.fail(function(error) {
								$editInMoxtra.data("moxtra-page-opening", false);
								log("Moxtra authorization error " + error);
								// TODO notify the error to an user
							});
						} catch(e) {
							log("Error opening Moxtra page", e);
						}
					}
				}
				return false;
			});
		};
	}

	/**
	 * Moxtra JS wrapper for on-demand loading of the script. It is used in ExoMoxtra.
	 */
	function MoxtraJS(accessService, moxtrajs) {

		var exoWindow = window;

		var loader = $.Deferred();
		var initlzng = false;

		var addExoDocument = function(event) {
			var $tools = $("#UIMoxtraBinderSpaceTools");
			var $docSelector = $tools.find(".selectDocument");
			if ($docSelector.size() > 0) {
				var dsLink = $docSelector.attr("href");
				if (dsLink) {
					var ci = dsLink.lastIndexOf("')");
					if (ci > 0) {
						dsLink = dsLink.slice(0, ci);
						if (event.binder_id) {
							dsLink += "&binderId=" + event.binder_id;
						}
						if (event.session_key) {
							dsLink += "&sessionKey=" + event.session_key;
						}
						if (event.session_id) {
							dsLink += "&sessionId=" + event.session_id;
						}
						dsLink += "')";
					}
					exoWindow.focus();
					eval(dsLink);
				} else {
					log("ERROR: document selector tool not found");
				}
			} else {
				log("ERROR: tools not found");
			}
		};

		var load = function() {
			if (loader.state() === "pending") {
				function init() {
					if (!initlzng) {
						// for avoiding double intialization
						initlzng = true;
						var initlzr = accessService();
						initlzr.done(function(token) {
							var options = {
								mode : "production",
								client_id : token.clientId,
								access_token : token.accessToken,
								invalid_token : function(event) {
									log("Access Token expired for session id: " + event.session_id);
									// block use of api until a new access token will be load from the REST service
									loader = $.Deferred();
									init();
								}
							};

							if (!moxtrajs) {
								// try use globals
								moxtrajs = Moxtra;
							}
							if (moxtrajs) {
								moxtrajs.init(options);
								loader.resolve(moxtrajs);
								log("Moxtra JS SDK initialized");
							} else {
								loader.reject("Moxtra script not found");
							}
						});
						initlzr.fail(function(error) {
							loader.reject("Cannot initialize Moxtra script: " + error);
						});
						initlzr.always(function() {
							initlzng = false;
						});
					}
				}

				if (moxtrajs) {
					// init given Moxtra JS API
					init();
				} else {
					// load Moxtra JS API, then init it
					var jsLoader = loadScript("https://www.moxtra.com/api/js/moxtra-latest.js", "moxtrajs");
					jsLoader.done(function() {
						log("Moxtra JS API loaded");
						init();
					});
					jsLoader.fail(function(error) {
						loader.reject("Cannot load Moxtra script: " + error);
					});
				}
			}
			return loader.promise();
		};

		var invoke = function(eventName, event, callbacks) {
			if (callbacks) {
				var action = callbacks[eventName];
				if (action) {
					action(event);
				}
			}
		};

		/**
		 * Preload MoxtraJS to avoid popup blocker in event.
		 */
		this.preload = function() {
			return load();
		};

		this.showBinder = function(binderId, elemId, callbacks) {
			var process = $.Deferred();
			var apiReady = load();
			apiReady.done(function(api) {
				var options = {
					binder_id : binderId,
					start_chat : function(event) {
						log("ChatView started session Id: " + event.session_id);
						invoke("start_chat", event, callbacks);
					},
					share : function(event) {
						log("Share session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("share", event);
					},
					error : function(event) {
						log("ChatView error code: " + event.error_code + " error message: " + event.error_message);
						invoke("error", event, callbacks);
					},
					publish_feed : function(event) {
						log("publish_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("publish_feed", event, callbacks);
					},
					receive_feed : function(event) {
						log("receive_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("receive_feed", event, callbacks);
					},
					start_note : function(event) {
						log("start_note session Id: " + event.session_id + " session key: " + event.session_key);
						invoke("start_note", event, callbacks);
					}
				};
				if ($("#UIMoxtraBinderSpaceTools").size() > 0) {
					options.extension = {
						"menus" : [{
							"add_page" : [{
								"menu_name" : "Add eXo Document",
								"position" : "bottom"
							}]
						}]
					};
					options.add_page = function(event) {
						if (event.action == "Add eXo Document") {
							//alert("Clicked on eXo Document for Binder Id: " + event.binder_id);
							addExoDocument(event);
						}
					};
				}
				if (elemId) {
					options.tagid4iframe = elemId;
					options.iframe = true;
				}
				api.chat(options);
				process.resolve(api);
			});
			apiReady.fail(function(error) {
				process.reject(error);
			});
			return process.promise();
		};

		this.showTimeline = function(binderId, elemId, callbacks) {
			var process = $.Deferred();
			var apiReady = load();
			apiReady.done(function(api) {
				var options = {
					binder_id : binderId,
					autostart_meet : true,
					autostart_note : true,
					start_timeline : function(event) {
						log("start_timeline started session Id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("start_timeline", event, callbacks);
					},
					view_binder : function(event) {
						log("view_binder started session Id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("view_binder", event, callbacks);
					},
					invite_member : function(event) {
						log("invite_member into binder Id: " + event.binder_id);
						invoke("invite_member", event, callbacks);
					},
					remove_binder_user : function(event) {
						log("remove_binder_user from binder Id: " + event.binder_id);
						invoke("remove_binder_user", event, callbacks);
					},
					start_chat : function(event) {
						log("ChatView started session Id: " + event.session_id);
						invoke("start_chat", event, callbacks);
					},
					share : function(event) {
						log("Share session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("share", event);
					},
					error : function(event) {
						log("ChatView error code: " + event.error_code + " error message: " + event.error_message);
						invoke("error", event, callbacks);
					},
					publish_feed : function(event) {
						log("publish_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("publish_feed", event, callbacks);
					},
					receive_feed : function(event) {
						log("receive_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("receive_feed", event, callbacks);
					},
					start_note : function(event) {
						log("start_note session Id: " + event.session_id + " session key: " + event.session_key);
						invoke("start_note", event, callbacks);
					}
				};
				if ($("#UIMoxtraBinderSpaceTools").size() > 0) {
					options.extension = {
						"menus" : [{
							"add_page" : [{
								"menu_name" : "Add eXo Document",
								"position" : "bottom"
							}]
						}]
					};
					options.add_page = function(event) {
						if (event.action == "Add eXo Document") {
							//alert("Clicked on eXo Document for Binder Id: " + event.binder_id);
							addExoDocument(event);
						}
					};
				}
				if (elemId) {
					options.tagid4iframe = elemId;
					options.iframe = true;
				}
				api.timeline(options);
				process.resolve(api);
			});
			apiReady.fail(function(error) {
				process.reject(error);
			});
			return process.promise();
		};

		this.showPages = function(binderId, pageId, elemId, callbacks) {
			var process = $.Deferred();
			var apiReady = load();
			apiReady.done(function(api) {
				var options = {
					binder_id : binderId,
					iframe : true,
					tagid4iframe : elemId,
					start_page : function(event) {
						log("PageView started session Id: " + event.session_id);
						invoke("start_page", event, callbacks);
					},
					share : function(event) {
						log("Share session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("share", event);
					},
					error : function(event) {
						log("PageView error code: " + event.error_code + " error message: " + event.error_message);
						invoke("error", event, callbacks);
					},
					publish_feed : function(event) {
						log("publish_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("publish_feed", event, callbacks);
					},
					receive_feed : function(event) {
						log("receive_feed session Id: " + event.session_id + " binder Id: " + event.binder_id + " page Ids: " + event.page_id);
						invoke("receive_feed", event, callbacks);
					},
					start_note : function(event) {
						log("start_note session Id: " + event.session_id + " session key: " + event.session_key);
						invoke("start_note", event, callbacks);
					}
				};
				if ($("#UIMoxtraBinderSpaceTools").size() > 0) {
					options.extension = {
						"menus" : [{
							"add_page" : [{
								"menu_name" : "eXo Document",
								"position" : "bottom"
							}]
						}]
					};
					options.add_page = function(event) {
						if (event.action == "eXo Document") {
							//alert("Clicked on eXo Document for Binder Id: " + event.binder_id);
							addExoDocument(event);
						}
					};
				}
				if (pageId) {
					options.page_id = pageId;
				}
				api.pageView(options);
				process.resolve(api);
			});
			apiReady.fail(function(error) {
				process.reject(error);
			});
			return process.promise();
		};

		this.startMeet = function(binderId, callbacks) {
			var process = $.Deferred();
			var apiReady = load();
			apiReady.done(function(api) {
				var options = {
					iframe : false,
					start_meet : function(event) {
						log("start_meet: session key: " + event.session_key + " session id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("start_meet", event, callbacks);
					},
					error : function(event) {
						log("error: error code: " + event.error_code + " message: " + event.error_message);
						invoke("error", event, callbacks);
					},
					resume_meet : function(event) {
						log("resume_meet: session key: " + event.session_key + " session id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("resume_meet", event, callbacks);
					},
					end_meet : function(event) {
						log("end_meet: Meet end event");
						invoke("end_meet", event, callbacks);
					}
				};
				if ($("#UIMoxtraBinderSpaceTools").size() > 0) {
					options.extension = {
						"show_dialogs" : {
							"meet_invite" : true
						},
						"menus" : [{
							"add_page" : [{
								"menu_name" : "My eXo Document",
								"position" : "bottom"
							}]
						}]
					};
					options.add_page = function(event) {
						if (event.action == "My eXo Document") {
							//alert("Clicked on eXo Document for Binder Id: " + event.binder_id);
							//window.open(exoWindow.location.href + "#exo-document-selector", "_blank");
							addExoDocument(event);
						}
					};
				}
				if (binderId) {
					options.schedule_binder_id = binderId;
				}
				api.meet(options);
				process.resolve(api);
			});
			apiReady.fail(function(error) {
				process.reject(error);
			});
			return process.promise();
		};

		this.joinMeet = function(sessionKey, callbacks) {
			var process = $.Deferred();
			var apiReady = load();
			apiReady.done(function(api) {
				var options = {
					iframe : false,
					session_key : sessionKey,
					start_meet : function(event) {
						log("start_meet: session key: " + event.session_key + " session id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("start_meet", event, callbacks);
					},
					error : function(event) {
						log("error: error code: " + event.error_code + " message: " + event.error_message);
						invoke("error", event, callbacks);
					},
					exit_meet : function(event) {
						log("exit_meet: session key: " + event.session_key + " session id: " + event.session_id + " binder id: " + event.binder_id);
						invoke("exit_meet", event, callbacks);
					},
					end_meet : function(event) {
						log("end_meet: Meet end event");
						invoke("end_meet", event, callbacks);
					}
				};
				if ($("#UIMoxtraBinderSpaceTools").size() > 0) {
					options.extension = {
						"show_dialogs" : {
							"meet_invite" : true
						},
						"menus" : [{
							"add_page" : [{
								"menu_name" : "My eXo Document",
								"position" : "bottom"
							}]
						}]
					};
					options.add_page = function(event) {
						if (event.action == "My eXo Document") {
							//alert("Clicked on eXo Document for Binder Id: " + event.binder_id);
							//window.open(exoWindow.location.href + "#exo-document-selector", "_blank");
							addExoDocument(event);
						}
					};
				}
				api.joinMeet(options);
				process.resolve(api);
			});
			apiReady.fail(function(error) {
				process.reject(error);
			});
			return process.promise();
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
			//loadStyle("/moxtra/skin/jquery-ui.css");
			loadStyle("/moxtra/skin/jquery.pnotify.default.css");
			loadStyle("/moxtra/skin/jquery.pnotify.default.icons.css");
			loadStyle("/moxtra/skin/exo-moxtra.css");

			// configure Pnotify
			// use jQuery UI css
			//$.pnotify.defaults.styling = "jqueryui";
			// no history roller in the right corner
			$.pnotify.defaults.history = false;

			// init Meet button
			client.initMeetButton();
		} catch(e) {
			log("Error configuring Moxtra styles.", e);
		}
	}

	return client;
})($);
