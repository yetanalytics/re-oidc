.phony: clean, keycloak-demo, test, ci

clean:
	rm -rf target

keycloak-demo:
	cd keycloak; docker compose up

test:
	clojure -Mfig:test

ci:
	clojure -Mfig:test-ci
