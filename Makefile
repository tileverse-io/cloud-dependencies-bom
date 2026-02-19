.PHONY: all
all: install

TAG=$(shell ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)

.PHONY: help
help:
	@echo "Cloud Dependencies BOM - Available targets:"
	@echo ""
	@echo "Build targets:"
	@echo "  clean         - Clean build artifacts"
	@echo "  install       - Install BOM to local repository"
	@echo "  verify        - Full verification (lint + validate)"
	@echo ""
	@echo "Code quality targets:"
	@echo "  format        - Sort POM file"
	@echo "  lint          - Check POM formatting without applying changes"
	@echo ""
	@echo "Utility targets:"
	@echo "  info          - Show project information"

.PHONY: clean
clean:
	./mvnw clean -ntp

.PHONY: format
format:
	./mvnw sortpom:sort -ntp

.PHONY: lint
lint:
	./mvnw -Pqa validate -ntp

.PHONY: install
install:
	./mvnw install -ntp

.PHONY: verify
verify: lint install

.PHONY: info
info:
	@echo "Project: Cloud Dependencies BOM"
	@echo "Version: $(TAG)"
	@echo "Maven coordinates: io.tileverse:cloud-dependencies-bom"
	@echo ""
	@echo "Managed cloud SDKs:"
	@echo "  - AWS SDK v2: $(shell ./mvnw help:evaluate -Dexpression=aws-sdk.version -q -DforceStdout)"
	@echo "  - Azure Storage Blob: $(shell ./mvnw help:evaluate -Dexpression=azure-storage-blob.version -q -DforceStdout)"
	@echo "  - Azure Identity: $(shell ./mvnw help:evaluate -Dexpression=azure-identity.version -q -DforceStdout)"
	@echo "  - Google Cloud Storage: $(shell ./mvnw help:evaluate -Dexpression=google-cloud-storage.version -q -DforceStdout)"
