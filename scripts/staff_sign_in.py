#!/usr/bin/env python3
import base64
import hashlib
import html
import http.cookiejar
import json
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

CLIENT_ID = "jaypay-admin-console"
ENDPOINTS = "/realms/staff/protocol/openid-connect"
REFUSED = 3


class StopAtCallback(urllib.request.HTTPRedirectHandler):
    def __init__(self, callback):
        self.callback = callback

    def redirect_request(self, request, fp, code, message, headers, url):
        if url.startswith(self.callback):
            raise CallbackReached(url)
        return super().redirect_request(request, fp, code, message, headers, url)


class CallbackReached(Exception):
    def __init__(self, url):
        self.url = url


def base64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def form_action(page):
    match = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', page)
    if not match:
        return None
    return html.unescape(match.group(1))


def page_error(page):
    for marker in ('id="input-error-username"', 'id="input-error-password"', "pf-v5-c-alert__title"):
        start = page.find(marker)
        if start >= 0:
            text = re.search(r">\s*([^<>]*\S)\s*<", page[start:])
            if text:
                return html.unescape(text.group(1))
    return "the sign-in page did not redirect back"


def sign_in(site, username, password):
    callback = f"{site}/callback"
    verifier = base64url(secrets.token_bytes(32))
    state = base64url(secrets.token_bytes(16))
    challenge = base64url(hashlib.sha256(verifier.encode()).digest())
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), StopAtCallback(callback))
    query = urllib.parse.urlencode(
        {
            "client_id": CLIENT_ID,
            "response_type": "code",
            "scope": "openid",
            "redirect_uri": callback,
            "state": state,
            "code_challenge": challenge,
            "code_challenge_method": "S256",
        }
    )
    try:
        page = opener.open(f"{site}{ENDPOINTS}/auth?{query}", timeout=30).read().decode()
        action = form_action(page)
        if not action:
            return None, "the authorization endpoint did not show a sign-in form"
        credentials = urllib.parse.urlencode({"username": username, "password": password}).encode()
        page = opener.open(action, data=credentials, timeout=30).read().decode()
        return None, page_error(page)
    except CallbackReached as reached:
        params = urllib.parse.parse_qs(urllib.parse.urlparse(reached.url).query)
        if params.get("state") != [state]:
            return None, "the callback carried another state"
        token_request = urllib.parse.urlencode(
            {
                "client_id": CLIENT_ID,
                "grant_type": "authorization_code",
                "code": params["code"][0],
                "redirect_uri": callback,
                "code_verifier": verifier,
            }
        ).encode()
        with urllib.request.urlopen(f"{site}{ENDPOINTS}/token", data=token_request, timeout=30) as reply:
            return json.load(reply), None
    except urllib.error.HTTPError as error:
        return None, f"HTTP {error.code} from {error.url}"


def main():
    site, username, password = sys.argv[1], sys.argv[2], sys.argv[3]
    tokens, refusal = sign_in(site.rstrip("/"), username, password)
    if tokens is None:
        print(refusal, file=sys.stderr)
        sys.exit(REFUSED)
    json.dump(tokens, sys.stdout)


if __name__ == "__main__":
    main()
