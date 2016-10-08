# AndroidHotFix

## 什么是热修复
> 热修复就是打补丁，当一个app已经上线之后如果出现一个严重的bug，这时再去打个打包发布版本是很费劲的，对用户来说再去下载一个新版本的体验也是不好的，所以就出现的热修复，也就是线打补丁。

## 热修复原理
1. Android的类加载机制

    Android的类加载器分为两种,PathClassLoader和DexClassLoader，两者都继承自BaseDexClassLoader

    PathClassLoader代码位于libcore\dalvik\src\main\java\dalvik\system\PathClassLoader.java
    DexClassLoader代码位于libcore\dalvik\src\main\java\dalvik\system\DexClassLoader.java
    BaseDexClassLoader代码位于libcore\dalvik\src\main\java\dalvik\system\BaseDexClassLoader.java

    * PathClassLoader
      >用来加载系统类和应用类

    * DexClassLoader

      >用来加载jar、apk、dex文件.加载jar、apk也是最终抽取里面的Dex文件进行加载.



2. 热修复机制

看下PathClassLoader代码

```
public class PathClassLoader extends BaseDexClassLoader {

    public PathClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }

    public PathClassLoader(String dexPath, String libraryPath,
            ClassLoader parent) {
        super(dexPath, null, libraryPath, parent);
    }
}
```
DexClassLoader代码

```
public class DexClassLoader extends BaseDexClassLoader {

    public DexClassLoader(String dexPath, String optimizedDirectory, String libraryPath, ClassLoader parent) {
        super(dexPath, new File(optimizedDirectory), libraryPath, parent);
    }
}
```

两个ClassLoader就两三行代码,只是调用了父类的构造函数.

```
public class BaseDexClassLoader extends ClassLoader {
    private final DexPathList pathList;

    public BaseDexClassLoader(String dexPath, File optimizedDirectory,
            String libraryPath, ClassLoader parent) {
        super(parent);
        this.pathList = new DexPathList(this, dexPath, libraryPath, optimizedDirectory);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Throwable> suppressedExceptions = new ArrayList<Throwable>();
        Class c = pathList.findClass(name, suppressedExceptions);
        if (c == null) {
            ClassNotFoundException cnfe = new ClassNotFoundException("Didn't find class \"" + name + "\" on path: " + pathList);
            for (Throwable t : suppressedExceptions) {
                cnfe.addSuppressed(t);
            }
            throw cnfe;
        }
        return c;
    }
```
在BaseDexClassLoader 构造函数中创建一个DexPathList类的实例,这个DexPathList的构造函数会创建一个dexElements 数组
```
public DexPathList(ClassLoader definingContext, String dexPath, String libraryPath, File optimizedDirectory) {
        ...
        this.definingContext = definingContext;
        ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
        //创建一个数组
        this.dexElements = makeDexElements(splitDexPath(dexPath), optimizedDirectory, suppressedExceptions);
        ...
    }
```
然后BaseDexClassLoader 重写了findClass方法,调用了pathList.findClass，跳到DexPathList类中.

```
/* package */final class DexPathList {
    ...
    public Class findClass(String name, List<Throwable> suppressed) {
            //遍历该数组
        for (Element element : dexElements) {
            //初始化DexFile
            DexFile dex = element.dexFile;

            if (dex != null) {
                //调用DexFile类的loadClassBinaryName方法返回Class实例
                Class clazz = dex.loadClassBinaryName(name, definingContext, suppressed);
                if (clazz != null) {
                    return clazz;
                }
            }
        }
        return null;
    }
    ...
}
```
会遍历这个数组,然后初始化DexFile，如果DexFile不为空那么调用DexFile类的loadClassBinaryName方法返回Class实例.
归纳上面的话就是:ClassLoader会遍历这个数组,然后加载这个数组中的dex文件.
而ClassLoader在加载到正确的类之后,就不会再去加载有Bug的那个类了,我们把这个正确的类放在Dex文件中,让这个Dex文件排在dexElements数组前面即可.

### 热修复框架有哪些

* https://github.com/dodola/HotFix
* https://github.com/jasonross/Nuwa
* https://github.com/bunnyblue/DroidFix
* https://github.com/alibaba/AndFix

### AndFix使用方法
> https://github.com/alibaba/AndFix
 * AndFix is a solution to fix the bugs online instead of redistributing Android App. It is distributed as Android Library.
 * Andfix is an acronym for "Android hot-fix".
 * AndFix supports Android version from 2.3 to 7.0, both ARM and X86 architecture, both Dalvik and ART runtime, both 32bit and 64bit.
 * The compressed file format of AndFix's patch is .apatch. It is dispatched from your own server to client to fix your App's bugs.

1. 添加依赖

    ```
    compile 'com.alipay.euler:andfix:0.5.0@aar'
    ```
2. Application.onCreate() 中添加以下代码

    ```
    patchManager = new PatchManager(this);
    String appversion= getPackageManager().getPackageInfo(getPackageName(), 0).versionName;//获取appversion
    patchManager.init(appversion);//current version
    patchManager.loadPatch();
    String patchFileString = "/sdcard/patch.apatch";//本Demo用的本地路径测试的，正常应该用网络下载差异包
    File apatchPath = new File(patchFileString);
    if (apatchPath.exists()) {
        Log.i(TAG, "补丁文件存在");
        try {
            patchManager.addPatch(patchFileString);
        } catch (IOException e) {
            Log.i(TAG, "打补丁出错了"+e.getMessage());
            e.printStackTrace();
        }
    } else {
        Log.i(TAG, "补丁文件不存在");
    }
    ```
`不同的版本是不能做补丁的，无法用apkpatch做差异包：java.lang.RuntimeException: can,t modified Field:VERSION_NAME`

3. 生成补丁文件
> 注意每次appversion变更都会导致所有补丁被删除,如果appversion没有改变，则会加载已经保存的所有补丁。
然后在需要的地方调用PatchManager的addPatch方法加载新补丁，比如可以在下载补丁文件之后调用。
之后就是打补丁的过程了，首先生成一个apk文件，然后更改代码，在修复bug后生成另一个apk。
通过官方提供的工具apkpatch(本Demo中apkpatch-1.0.3就是工具)
生成一个.apatch格式的补丁文件，需要提供原apk，修复后的apk，以及一个签名文件。
可以直接使用命令apkpatch查看具体的使用方法。

使用方法：

ubuntu命令使用方法：sh apkpatch.sh -f 2.apk -t 1.apk -o Dennis -k keystore -p 123456 -a panmin -e 123456
```
生成差异文件命令 : apkpatch.bat -f new.apk -t old.apk -o output1 -k debug.keystore -p android -a androiddebugkey -e android
-f <new.apk> ：新版本
-t <old.apk> : 旧版本
-o <output> ： 输出目录
-k <keystore>： 打包所用的keystore
-p <password>： keystore的密码
-a <alias>： keystore 用户别名
-e <alias password>： keystore 用户别名密码
```
apkpatch原理：
>apkpatch将两个apk做一次对比，然后找出不同的部分。可以看到生成的apatch了文件，后缀改成zip再解压开，里面有一个dex文件。通过jadx查看一下源码，里面就是被修复的代码所在的类文件,这些更改过的类都加上了一个_CF的后缀，并且变动的方法都被加上了一个叫@MethodReplace的annotation，通过clazz和method指定了需要替换的方法。
然后客户端sdk得到补丁文件后就会根据annotation来寻找需要替换的方法。最后由JNI层完成方法的替换。

4. AndFix缺点

不支持YunOS
无法添加新类和新的字段
需要使用加固前的apk制作补丁，但是补丁文件很容易被反编译，也就是修改过的类源码容易泄露。
使用加固平台可能会使热补丁功能失效
Application的onCreate里面AndFix相关的逻辑，一定要区分进程因为如果你的app是多进程的，每个进程都会创建Application对象，导致你的补丁逻辑被重复执行。在内存层面看，补丁操作的影响只会局限在进程之内，似乎没有什么关系，但是如果你的补丁操作涉及到文件系统的操作，比如拷贝文件、删除文件、解压文件等等，那么进程之间就会相互影响了。我们遇到的问题就是在主进程里面下载好的补丁包会莫名其妙地不见，主进程下载好补丁包后，信鸽进程被启动，创建Application对象，执行补丁逻辑，把刚刚主进程下载好的补丁包应用了，然后又把补丁包删除。。。。。。

5. AndFix支持Multidex的解决方案
> 对于超过65535的问题，要通过Google提供的方案multidex，如果用这种方式就会发现只有Activity里的可以修复，类和Fragment里的方法是修复不了，因为Activity是在主dex里，其他的可能在生成的第二个dex里
<http://w4lle.github.io/2016/03/13/AndFix支持multidex解决方案/?utm_source=tuicool&utm_medium=referral>
<https://github.com/w4lle/andfix_apkpatch_support_multidex>



### 参考资料
http://blog.csdn.net/theone10211024/article/details/50275027
andfix多次修改同一个方法报错的解决：http://www.jianshu.com/p/58fc4c2cb65a
http://blog.csdn.net/yanzeyanga/article/details/51384254
