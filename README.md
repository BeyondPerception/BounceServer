# BounceServer

The most important, and probably most simple, componant of the mill program suite.

This program accepts up to 32 connections as connection pairs, and writes all data from one connection in a pair to the corresponding connection. This exists to allow the manufacturing facility and NASA to keep their firewalls closed and safe from security vulerabilities.

After the authentication and identification phase of a connection, this server becomes dumb and writes all data it receives.
