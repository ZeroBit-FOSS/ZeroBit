# Third-party notices

ZeroBit uses the following third-party software:

## SnakeYAML Engine

- Project: <https://bitbucket.org/snakeyaml/snakeyaml-engine>
- Copyright: SnakeYAML contributors
- License: Apache License 2.0
- License text: <https://www.apache.org/licenses/LICENSE-2.0>

SnakeYAML Engine is used to parse the restricted YAML 1.2 syntax accepted by
OpenMacro. ZeroBit adds its own validation and rejects YAML features outside
that subset.

## kotlinx.coroutines

- Project: <https://github.com/Kotlin/kotlinx.coroutines>
- Copyright: JetBrains and Kotlin contributors
- License: Apache License 2.0
- License text: <https://www.apache.org/licenses/LICENSE-2.0>

kotlinx.coroutines provides cancellable background parsing for the OpenMacro
source editor without blocking Android's main UI thread.
