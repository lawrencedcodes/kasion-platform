# Contributing to Kasion

First off, thank you for considering contributing to Kasion! We're excited to build the future of Java deployments with your help. This document provides guidelines for contributing to the project.

## How Can I Contribute?

### Reporting Bugs

If you find a bug, please ensure the bug was not already reported by searching on GitHub under [Issues](https://github.com/lawrencedcodes/kasion-platform/issues).

If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/lawrencedcodes/kasion-platform/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

### Suggesting Enhancements

If you have an idea for a new feature or an enhancement to an existing one, please [open an issue](https://github.com/lawrencedcodes/kasion-platform/issues/new) to start a discussion. This allows us to coordinate our efforts and prevent duplication of work.

### Code Contributions

We welcome pull requests! If you're ready to contribute code, please follow these steps:

1.  **Fork the repository** and create your branch from `main`.
2.  **Set up your development environment:** Ensure you have Java 21, Docker, and Git installed. You can run the `setup.sh` script to configure the necessary components.
3.  **Make your changes:** Adhere to the existing code style and conventions.
4.  **Add tests:** If you're adding a new feature, please include unit or integration tests.
5.  **Ensure the test suite passes:** Run `./mvnw clean verify` in the `control-plane` directory.
6.  **Issue a pull request:** Once you're happy with your changes, issue a pull request to the `main` branch.

## Development Setup

*   **Backend:** The control plane is a standard Spring Boot application located in the `control-plane` directory. You can open this as a project in your favorite IDE (e.g., IntelliJ IDEA).
*   **Frontend:** The UI is composed of Thymeleaf templates located in `control-plane/src/main/resources/templates`.
*   **Stack:** The entire Kasion stack (including Prometheus, Grafana, etc.) can be run locally using Docker Compose via the `control-plane/compose.yaml` file.

## Code Style

*   Please follow the existing code style. We generally adhere to the standard Java and Spring Boot conventions.
*   Use meaningful variable names and add comments only for complex or non-obvious logic.

Thank you for helping us make Kasion a great tool for the Java community!
