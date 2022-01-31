.phony: clean, keycloak-demo

clean:
	rm -rf target

keycloak-demo:
	cd keycloak; docker compose up
