#!/bin/bash

PATHTOOL=cygpath

DRIVEPREFIX=/cygdrive
DRIVEPREFIX=
ENVROOT="c:\cygwin64"
#ENVROOT='\\wsl$\Ubuntu-20.04'

TEMPDIRS=""
trap "cleanup" EXIT

# Make regexp tests case insensitive
shopt -s nocasematch

cleanup() {
  if [[ "$TEMPDIRS" != "" ]]; then
    rm -rf $TEMPDIRS
  fi
}

cygwin_convert_to_win() {
  converted=""

  old_ifs="$IFS"
  IFS=":"
  for arg in $1; do
    if [[ $arg =~ (^[^/]*)($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
      # Start looking for drive prefix
      arg="${BASH_REMATCH[1]}${BASH_REMATCH[3]}:${BASH_REMATCH[4]}"
    elif [[ $arg =~ (^[^/]*)(/[^/]+/[^/]+.*$) ]] ; then
      # Does arg contain a potential unix path? Check for /foo/bar
      arg="${BASH_REMATCH[1]}$ENVROOT${BASH_REMATCH[2]}"
    fi

    if [[ "$converted" = "" ]]; then
      converted="$arg"
    else
      converted="$converted:$arg"
    fi
  done
  IFS="$old_ifs"

  result="$converted"
}

cygwin_convert_at_file() {
  infile="$1"
  if [[ -e $infile ]] ; then
    tempdir=$(mktemp -dt fixpath.XXXXXX)
    TEMPDIRS="$TEMPDIRS $tempdir"

    while read line; do
      cygwin_convert_to_win "$line" >> $tempdir/atfile
    done < $infile
    result="@$tempdir/atfile"
  else
    result="@$infile"
  fi
}

cygwin_import_to_unix() {
  path="$1"

  if [[ $path =~ ^.:[/\\].*$ ]] ; then
    # We really don't want windows paths as input, but try to handle them anyway
    path="$($PATHTOOL -u "$path")"
    # Path will now be absolute
  else
    # Make path absolute, and resolve '..' in path
    dirpart="$(dirname "$path")"
    dirpart="$(cd "$dirpart" 2>&1 > /dev/null && pwd)"
    if [[ $? -ne 0 ]]; then
      echo fixpath: failure: Directory "'"$path"'" does not exist 1>&2
      exit 1
    fi
    basepart="$(basename "$path")"
    path="$dirpart/$basepart"
  fi

  # Now turn it into a windows path
  winpath="$($PATHTOOL -w "$path")"

  # On WSL1, PATHTOOL will fail for files in envroot. We assume that if PATHTOOL
  # fails, we have a valid unix path in path.

  if [[ $? -eq 0 ]]; then
    if [[ ! "$winpath" =~ ^"$ENVROOT"\\.*$ ]] ; then
      # If it is not in envroot, it's a generic windows path
      if [[ ! $winpath =~ ^[-_.:\\a-zA-Z0-9]*$ ]] ; then
        # Path has forbidden characters, rewrite as short name
        shortpath="$(cmd.exe /q /c for %I in \( "$winpath" \) do echo %~sI | tr -d \\n\\r)"
        path="$($PATHTOOL -u "$shortpath")"
        # Path is now unix style, based on short name
      fi
      # Make it lower case
      path="$(echo "$path" | tr [:upper:] [:lower:])"
    fi
  fi

  if [[ "$path" =~ " " ]]; then
    echo fixpath: failure: Path "'"$path"'" contains space 1>&2
    exit 1
  fi

  result="$path"
}


os_env="$1"
action="$2"
shift 2

if [[ "$action" == "exec-detach" ]] ; then
  DETACH=true
  action=exec
fi

if [[ "$action" == "print" ]] ; then
  args=""
  for arg in "$@" ; do
    if [[ $arg =~ ^@(.*$) ]] ; then
      cygwin_convert_at_file "${BASH_REMATCH[1]}"
    else
      cygwin_convert_to_win "$arg"
    fi
    args="$args$result "
  done
  # FIXME: fix quoting?
  echo "$args"
elif [[ "$action" == "import" ]] ; then
  orig="$1"
  cygwin_import_to_unix "$orig"
  echo "$result"
elif [[ "$action" == "exec" ]] ; then
  for arg in "$@" ; do

    win_style=$(cygwin_convert_to_win "$arg")
    args="$args $win_style"
  done
  # FIXME: fix quoting
  echo "$args"
else
  echo Unknown operation: "$action"
fi
