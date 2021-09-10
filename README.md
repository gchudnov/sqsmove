# SQSMove

Moves messages between SQS queues with the desired parallelism, preserving attributes.

## Usage

```bash
$ sqsmove --help
```

```text
sqsmove 1.0.0
Usage: sqsmove [options]

  -s, --src-queue <name>   source queue name
  -d, --dst-queue <name>   destination queue name
  -n, --parallelism <value>
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
sqsmove -s A -d B -n 1
```

## Build

Follow the instructions, listed in [BUILD](res/graalvm/BUILD.md) readme.

## Contact

[Grigorii Chudnov](mailto:g.chudnov@gmail.com)

## License

Distributed under the [The MIT License (MIT)](LICENSE).
