# SQSMove

<img src="res/img/logo192.png" width="192px" height="192px" align="right" />

[![Build](https://github.com/gchudnov/sqsmove/actions/workflows/ci.yml/badge.svg)](https://github.com/gchudnov/sqsmove/actions/workflows/ci.yml)

Moves or copies messages between AWS SQS queues with the desired parallelism, preserving attributes.

<br clear="right" /><!-- Turn off the wrapping for the logo image. -->

## Usage

Download the binary executable or build it.

```bash
$ sqsmove --help
```

```text
sqsmove 1.1.0
Usage: sqsmove [options]

  -s, --src-queue <name>   source queue name
  -d, --dst-queue <name>   destination queue name
  --src-dir <path>         source directory path
  --dst-dir <path>         destination directory path
  -p, --parallelism <value>
                           parallelism (default: 16)
  --visibility-timeout <value>
                           visibility timeout (default: 30s). Format: 1d12h35m16s
  --no-delete              do not delete messages after processing
  --yes                    do not ask for confirmation
  -v, --verbose            verbose output
  -h, --help               prints this usage text
  --version                prints the version
```

## Examples

**Move messages from queue A to queue B:**

```bash
sqsmove -s A -d B
```

**Move messages from queue A to queue B with parallelism 1:**

```bash
sqsmove -s A -d B -p 1
```

**Copy messages from queue A to queue B:**

```bash
sqsmove -s A -d B --no-delete
```

The flag `--no-delete` prevents the deletion of messages in the source queue. If there are *a lot* of messages in the source queue, it might be worth to increase the visibility timeout, e.g. `--visibility-timeout=5m` (the value depends on the expected time to copy all messages) to make sure that the utility is not copying the same message again when visibility timeout expires (default: 30s).

```bash
sqsmove -s A -d B --no-delete --visibility-timeout=5m
```

**Download messages to a local directory:**

```bash
sqsmove -s A --dst-dir D
```

It downloads both body of the message and attributes as two separate files and saves them to the specified directory.
If the message has id `X`, body will be saved to the file `X`, attributes to the file `X.meta`.

Attributes are stored as a CSV with 3 columns: `name`, `type` and `value` where each line is a separate attribute:

```text
name,type,value
strAttr,String,str
numAttr,Number,1
binAttr,Binary,QUJD
```

The supported types are `String`, `Number` and `Binary` where binary data is encoded as a Base64 string.

**Upload messages from a local directory:**

```bash
sqsmove --src-dir D -d B
```

When uploading messages from a local directory, files are not deleted. If there is a corresponding `.meta` file is present for a file, it will be used as a source of attributes.

**Confirm the action:**

When `sqsmove` is executed, it asks a user to confirm the action.
For example, `sqsmove --src-queue=A --dst-dir=D` generates the following output:

```text
Going to MOVE messages 'A' -> 'D'
[parallelism: 16; visibility-timeout: 30s; no-delete: false]
Are you sure? (y|N)
```

To avoid the question, provide `--yes` parameter:

```bash
sqsmove -s A -d D --yes
```

## Build

Follow the instructions, listed in [BUILD](res/graalvm/BUILD.md) readme.

## Contact

[Grigorii Chudnov](mailto:g.chudnov@gmail.com)

## License

Distributed under the [The MIT License (MIT)](LICENSE).
