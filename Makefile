.phony: clean, keycloak-demo, test, api-demo, figwheel-dev, serve-advanced

clean:
	rm -rf target

keycloak-demo:
	cd keycloak; docker compose up

api-demo:
	clojure -X:api-demo

figwheel-dev:
	clojure -M:dev:build

serve-advanced:
	clojure -M:dev:min

test:
	clojure -M:dev:test
