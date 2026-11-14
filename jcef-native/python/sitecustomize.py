# Loaded by the Python that JCEF's CMake build runs (PYTHONPATH, see buildJcef in build.gradle.kts).
#
# JCEF's tools/git_util.py takes a directory for a git checkout only when its `.git` is a directory, and tools/make_version_header.py fails the
# build otherwise ("Not a valid checkout"). In a submodule, `.git` is a file that points into the parent's repository, which git reads all the
# same: a `.git` file is a checkout too.
import os.path

_isdir = os.path.isdir


def _isdir_or_gitlink(path):
    return _isdir(path) or (os.path.basename(os.fspath(path)) == ".git" and os.path.isfile(path))


os.path.isdir = _isdir_or_gitlink
