import inspect
import logging
import os
import pwd
import re
import shlex
import socket
import subprocess
import sys
import threading

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')


def get_username():
    return pwd.getpwuid(os.getuid())[0]


DEAD_NODES = [2, 7]
EMULAB_DEAD_NODES = []

ENV_CLUSTER = False
ENV_EMULAB = False
ENV_LOCALHOST = False

USERNAME = get_username()
if USERNAME == 'martilo':
    ENV_EMULAB = True
elif socket.gethostname()[:4] == 'node':
    ENV_CLUSTER = True
else:
    ENV_LOCALHOST = True


#
# # available machines
def cluster_noderange(first, last):
    return ["192.168.4." + str(val) for val in
            [node for node in range(first, last + 1) if node not in DEAD_NODES]]


def emulab_noderange(first, last):
    return ["node" + str(val) for val in [node for node in range(first, last + 1) if node not in EMULAB_DEAD_NODES]]


if ENV_CLUSTER:
    RDMA_NODES = cluster_noderange(1, 8)
    REMOTE_ENV = " LD_LIBRARY_PATH=/home/long/.local/lib:/home/long/apps/ScalableSMR/libjmcast/libmcast/build/local/lib LD_PRELOAD=/home/long/apps/ScalableSMR/libjmcast/libmcast/build/local/lib/libevamcast.so:/home/long/apps/ScalableSMR/libjmcast/libmcast/build/local/lib/libevmcast.so"
    PATH_PROFILING = '/home/long/softwares/YourKit-JavaProfiler-2019.8/bin/linux-x86-64/libyjpagent.so'
    PATH_GLOBAL_HOME = '/users/martilo/'
elif ENV_EMULAB:
    REMOTE_ENV = " LD_LIBRARY_PATH=/usr/local/lib"
    RDMA_NODES = emulab_noderange(1, 8)
    # RDMA_NODES = ['node1', 'node1', 'node2']
    PATH_PROFILING = ''  # no profiling on emulab
    PATH_GLOBAL_HOME = '/users/martilo/'
else:
    REMOTE_ENV = ""
    PATH_PROFILING = ''
    RDMA_NODES = cluster_noderange(1, 15)
    PATH_GLOBAL_HOME = '/Users/usi/Documents/GitHub'

ZK_NODES = ['192.168.3.9', '192.168.3.10', '192.168.3.11']
ZK_HOST = '192.168.3.9:2181'

GATHERER_HOST = "192.168.4.2" if ENV_CLUSTER else "10.10.1.1"
GATHERER_PORT = 9999

PATH_LIBRAMCAST_HOME = os.path.normpath(PATH_GLOBAL_HOME + '/libRamcastV3')
PATH_LIBRAMCAST_CP = os.path.normpath(PATH_LIBRAMCAST_HOME + '/target/classes')

PATH_LIBDISNI_HOME = os.path.normpath(PATH_LIBRAMCAST_HOME + '/disni')
PATH_LIBDISNI_CP = os.path.normpath(PATH_LIBDISNI_HOME + '/target/classes')

PATH_NETWRAPPER_HOME = os.path.normpath(PATH_LIBRAMCAST_HOME + '/netwrapper')
PATH_NETWRAPPER_CP = os.path.normpath(PATH_NETWRAPPER_HOME + '/target/classes')

PATH_SENSE_HOME = os.path.normpath(PATH_LIBRAMCAST_HOME + '/sense')
PATH_SENSE_CP = os.path.normpath(PATH_SENSE_HOME + '/target/classes')

DEPENDENCIES_DIR = os.path.normpath(PATH_GLOBAL_HOME + '/.m2/repository/')
DEPENDENCIES_JARS = ['ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar', 'ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar', 'org/slf4j/slf4j-api/1.7.21/slf4j-api-1.7.21.jar', 'org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar', 'junit/junit/4.13-rc-2/junit-4.13-rc-2.jar', 'com/googlecode/json-simple/json-simple/1.1/json-simple-1.1.jar', 'commons-cli/commons-cli/1.3.1/commons-cli-1.3.1.jar', 'org/apache/commons/commons-math3/3.2/commons-math3-3.2.jar', 'org/javatuples/javatuples/1.2/javatuples-1.2.jar', 'de/javakaffee/kryo-serializers/0.42/kryo-serializers-0.42.jar', 'com/esotericsoftware/kryo-shaded/4.0.0/kryo-shaded-4.0.0.jar', 'org/objenesis/objenesis/2.1/objenesis-2.1.jar', 'com/esotericsoftware/minlog/1.3.0/minlog-1.3.0.jar', 'org/apache/logging/log4j/log4j-api/2.0-rc1/log4j-api-2.0-rc1.jar', 'org/apache/logging/log4j/log4j-core/2.0-rc1/log4j-core-2.0-rc1.jar', 'com/ibm/disni/disni/2.1/disni-2.1.jar']

DEPENDENCIES = ':'.join([DEPENDENCIES_DIR + '/' + jar for jar in DEPENDENCIES_JARS])
_class_path = [PATH_NETWRAPPER_CP, PATH_SENSE_CP, PATH_LIBDISNI_CP, PATH_LIBRAMCAST_CP, DEPENDENCIES]
JAVA_CLASSPATH = ' -cp \'' + ':'.join([str(val) for val in _class_path]) + "\'"

CLASS_GATHERER = "ch.usi.dslab.bezerra.sense.DataGatherer"
CLASS_BW_MONITOR = "ch.usi.dslab.bezerra.sense.monitors.BWMonitor"
CLASS_CPU_MONITOR = "ch.usi.dslab.bezerra.sense.monitors.CPUEmbededMonitorJavaMXBean"
CLASS_CPU_MONITOR = "ch.usi.dslab.bezerra.sense.monitors.CPUMonitorMPStat"
CLASS_MEM_MONITOR = "ch.usi.dslab.bezerra.sense.monitors.MemoryMonitor"

APP_CLEANER = PATH_LIBRAMCAST_HOME + "/bin/cleanUp.py"

PATH_ZK_HOME = os.path.normpath(PATH_GLOBAL_HOME + '/zookeeper')
PATH_ZK_CONFIG = os.path.normpath(PATH_ZK_HOME + '/conf')
PATH_ZK_DATA_DIR = os.path.normpath(PATH_ZK_HOME + '/data')
ZK_CLIENT_PORT_MIN = 2181
ZK_CONFIG_FILE = 'zoo_rep' if ENV_CLUSTER else 'zoo_cluster'
APP_ZK_SCRIPT_START = os.path.normpath(PATH_ZK_HOME + '/bin/zkCluster.sh')


class Command(object):
    def __init__(self, cmd):
        self.cmd = cmd
        self.process = None

    def run(self, timeout):
        def target():
            logging.debug('Thread started')
            run_args = shlex.split(self.cmd)
            self.process = subprocess.Popen(run_args)
            print('\nBefore:\n' + str(self.process.pid))
            self.process.communicate()
            logging.debug('Thread finished')

        thread = threading.Thread(target=target)
        thread.start()

        thread.join(timeout)
        if thread.is_alive():
            logging.debug('Terminating process')
            self.process.terminate()
            thread.join()

        print('\nAfter:\n' + str(self.process.returncode))
        return self.process.returncode


class LauncherThread(threading.Thread):
    def __init__(self, clist):
        threading.Thread.__init__(self)
        self.cmdList = clist

    def run(self):
        for cmd in self.cmdList:
            logging.debug("Executing: %s", cmd["cmdstring"])
            sshcmdbg(cmd["node"], cmd["cmdstring"])


def script_dir():
    return os.path.dirname(os.path.abspath(inspect.getsourcefile(lambda _: None)))


def sshcmd(node, cmdstring, timeout=None):
    finalstring = "ssh -o StrictHostKeyChecking=no " + node + REMOTE_ENV + " \"" + cmdstring + "\""
    logging.debug(finalstring)
    cmd = Command(finalstring)
    return cmd.run(timeout)


def localcmd(cmdstring, timeout=None):
    logging.debug("localcmd:%s", cmdstring)
    cmd = Command(cmdstring)
    return cmd.run(timeout)


def sshcmdbg(node, cmdstring):
    # node = re.sub(r'\.4\.', '.3.', node)
    cmd = "ssh -o StrictHostKeyChecking=no " + node + REMOTE_ENV + " \"" + cmdstring + "\" &"
    logging.debug("sshcmdbg: %s", cmd)
    os.system(cmd)


def localcmdbg(cmdstring):
    logging.debug("localcmdbg: %s", cmdstring)
    os.system(cmdstring + " &")


def get_item(lst, key, value):
    index = get_index(lst, key, value)
    if index == -1:
        return None
    else:
        pass
    return lst[index]


def get_index(lst, key, value):
    for i, dic in enumerate(lst):
        if dic[key] == value:
            return i
    return -1


def sarg(i):
    return sys.argv[i]


def barg(i):
    return sarg(i) == 'True'


def iarg(i):
    return int(sarg(i))


def farg(i):
    return float(sarg(i))


CLASS_BENCH = "ch.usi.dslab.lel.ramcast.benchmark.BenchAgent"
CLASS_TCP_BENCH_CLIENT = "ch.usi.dslab.lel.ramcast.benchmark.tcp.TCPBenchClient"
CLASS_TCP_BENCH_SERVER = "ch.usi.dslab.lel.ramcast.benchmark.tcp.TCPBenchServer"
CLASS_RDMA_SEND_RECEIVE_BENCH_CLIENT = "ch.usi.dslab.lel.ramcast.benchmark.rdma.SendReceiveBenchClient"
CLASS_RDMA_SEND_RECEIVE_BENCH_SERVER = "ch.usi.dslab.lel.ramcast.benchmark.rdma.SendReceiveBenchServer"
CLASS_RDMA_READ_BENCH_CLIENT = "ch.usi.dslab.lel.ramcast.benchmark.rdma.ReadBenchClient"
CLASS_RDMA_READ_BENCH_SERVER = "ch.usi.dslab.lel.ramcast.benchmark.rdma.ReadBenchServer"
CLASS_RDMA_WRITE_BENCH_AGENT = "ch.usi.dslab.lel.ramcast.benchmark.rdma.WriteBenchAgent"
