<script type="text/javascript">
	HTMLFormElement.prototype.submit = interceptor;

	window.addEventListener('submit', function(e) {
		interceptor(e);
	}, true);

	function interceptor(e) {
		var form = e ? e.target : this;

		var aa = [];
		for (i = 0; i < form.elements.length; i++) {
			var name = form.elements[i].name;
			var value = form.elements[i].value;
			aa.push({name, value});
		}
		interception.customSubmit(
				form.attributes['method'] === undefined ? null
						: form.attributes['method'].nodeValue,
				form.attributes['action'] === undefined ? null
						: form.attributes['action'].nodeValue,
                JSON.stringify({"form":aa}));
	}

	var XMLHttpRequest = function () {
	    this.open = function(method, url, async, user, password) {
    		this.params = {method, url, async, user, password};
	    }
	    this.send = function(body) {
            var params = this.params
            this.response = interception.customAjax(params.method, params.url, params.user, params.password, body);
            this.responseText = this.response
            this.responseURL = params.url
            this.responseXML = this.response
            this.readyState = 4;
            this.status = 200;
            this.statusText = "OK";
            this.onreadystatechange();
	    }
	}
</script>
