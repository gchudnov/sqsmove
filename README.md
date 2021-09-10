# SQSMove

<img src="res/img/logo192.png" width="192px" height="192px" align="right" />

[![Build](https://github.com/gchudnov/sqsmove/actions/workflows/ci.yml/badge.svg)](https://github.com/gchudnov/sqsmove/actions/workflows/ci.yml)

Moves messages between AWS SQS queues with the desired parallelism, preserving attributes.

<br clear="right" /><!-- Turn off the wrapping for the logo image. -->

## Usage

Download the binary executable or build it.

```bash
$ sqsmove --help
```

```text
sqsmove 1.0.0
Usage: sqsmove [options]

  -s, --src-queue <name>   source queue name
  -d, --dst-queue <name>   destination queue name
  -p, --parallelism <value>
                           desired parallelism (default: 16)
  -h, --help               prints this usage text
  -v, --verbose            verbose output
```

## Examples

Move messages from A to B:

```bash
sqsmove -s A -d B
```

Move messages from A to B with parallelism 1:

```bash
sqsmove -s A -d B -p 1
```

## Build

Follow the instructions, listed in [BUILD](res/graalvm/BUILD.md) readme.

## Contact

[Grigorii Chudnov](mailto:g.chudnov@gmail.com)

## License

Distributed under the [The MIT License (MIT)](LICENSE).
