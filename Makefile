.phony: clean, keycloak-demo, test

clean:
	rm -rf target

keycloak-demo:
	cd keycloak; docker compose up

test:
	clojure -Mfig:test
