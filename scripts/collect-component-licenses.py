#!/usr/bin/env python3
"""Record the actual resolved Android runtime artifacts; no network or credentials."""
from pathlib import Path
import io,json,os,re,zipfile,xml.etree.ElementTree as ET

project=Path(__file__).resolve().parent.parent
entries=json.loads((project/'.build/runtime-dependencies.json').read_text())
# AO worktree 使用独立 GRADLE_USER_HOME；优先从当前构建缓存读取 POM/通知。
cache_roots=[]
if os.environ.get('GRADLE_USER_HOME'):
    cache_roots.append(Path(os.environ['GRADLE_USER_HOME'])/'caches/modules-2/files-2.1')
cache_roots.append(Path.home()/'.gradle/caches/modules-2/files-2.1')
def artifact_dir(group,name,version):
    for root in cache_roots:
        candidate=root/group/name/version
        if candidate.is_dir(): return candidate
    return None
notices=project/'licenses/android-runtime-notices'
notices.mkdir(parents=True,exist_ok=True)
rows=[];records=[]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
for entry in entries:
    group,name,version=entry['group'],entry['name'],entry['version']
    coordinate=f'{group}:{name}:{version}'
    folder=notices/f'{group}-{name}-{version}'
    folder.mkdir(exist_ok=True)
    folder_dir=artifact_dir(group,name,version)
    poms=sorted(folder_dir.glob('*/*.pom')) if folder_dir else []
    declared=[]
    if poms:
        pom=poms[0]
        (folder/'published.pom').write_bytes(pom.read_bytes())
        tree=ET.fromstring(pom.read_bytes())
        for license in tree.findall('m:licenses/m:license',ns):
            declared.append({'name':license.findtext('m:name',default='',namespaces=ns),'url':license.findtext('m:url',default='',namespaces=ns)})
    copied=[]
    def collect(archive,prefix=''):
        for member in archive.infolist():
            leaf=member.filename.rsplit('/',1)[-1]
            if not re.match(r'(?i)^(license|notice|copying|copyright)(?:[._-].*)?$',leaf):continue
            if member.is_dir() or member.file_size>2*1024*1024:continue
            data=archive.read(member)
            try:data.decode('utf-8')
            except UnicodeDecodeError:continue
            filename=prefix+re.sub(r'[^A-Za-z0-9_.-]','_',member.filename)
            (folder/filename).write_bytes(data);copied.append(filename)
    with zipfile.ZipFile(entry['file']) as archive:
        collect(archive)
        if 'classes.jar' in archive.namelist():
            with zipfile.ZipFile(io.BytesIO(archive.read('classes.jar'))) as classes:collect(classes,'classes_')
    label='; '.join(item['name'] or item['url'] for item in declared) or 'POM 未声明，需人工核对'
    rows.append(f'| `{coordinate}` | {label} | `{entry["sha256"]}` |')
    records.append({k:v for k,v in entry.items() if k!='file'}|{'licenses':declared,'noticeFiles':copied})
text='# Android 运行依赖实际清单\n\n由 Gradle modernProbeRuntimeClasspath 实际解析结果生成，共 '+str(len(entries))+' 个 AAR/JAR。哈希对应构建缓存中的真实归档；许可证声明来自发布 POM，归档内可识别的 LICENSE / NOTICE 文本一同保留。此表不包含构建工具、测试库或手机首次下载的 Ubuntu / Node / DSH。完整法律审阅仍需核对缺失声明和上游要求。\n\n| 组件 | 发布 POM 的许可声明 | 归档 SHA-256 |\n|---|---|---|\n'+'\n'.join(rows)+'\n'
(project/'licenses/ANDROID-DEPENDENCIES.md').write_text(text)
(project/'licenses/android-runtime-dependencies.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
print(f'Inventoried {len(entries)} artifacts; unresolved POM licenses: {sum(not item["licenses"] for item in records)}')
