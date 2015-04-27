Moxtra integration in eXo
=========================

[Moxtra](http://www.moxtra.com) it is a team collaboration service offering tools for online communications such as chats and video meetings. An integration in eXo Platform allows eXo users leverage these tools to organize real-time conversations in their intranets and extranets. Moxtra integration consists of Meet integration in eXo Calendar events, quick Meetings with users in social streams and collaboration enhancements in eXo Spaces. Users may invite external participants to conversations in Moxtra withour their registration in eXo intranet or extranet.

Installation
------------

Users of Platform 4.1 and higher can simply install the add-on via *addon* tool from central catalog: select latest version, use "--unstable" key if want install latest development version. 

    ./addon install exo-moxtra

Configuration
-------------

Moxtra add-on accesses Moxtra API services using OAuth2 protocol. Thus your installation will need a registration of an [app in Moxtra](https://developer.moxtra.com/nextapps). Since you registred an app you have a client id and secret that should pointed in _exo.properties_ of your eXo Platform server. 

```ini
# eXo Moxtra
moxtra.client.id=YOUR_OAUTH2_ID
moxtra.client.secret=YOUR_OAUTH2_SECRET

```

Optionally you may need point your server name (required for OAuth2 redirects from Moxtra) and inform the add-on that HTTPS should be used. Note that to enable secure HTTPS it should be properly configured on your server also.

```ini
# eXo Moxtra
moxtra.client.schema=https
moxtra.client.host=YOUR_SERVER_HOST_NAME

```
 


