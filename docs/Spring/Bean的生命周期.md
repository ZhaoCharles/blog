# Bean的生命周期

Bean的生命周期，就是从创建Bean到销毁Bean的过程。在这个过程中，Spring提供了很多扩展点，这些扩展点具象化的表示就是各种BeanPostProcessor（Bean的后置处理器）的实现。本篇主要的内容就是介绍扫描Bean、实例化Bean、销毁Bean以及中间夹杂着执行各种Bean的后置处理器的过程。

下面的流程图包括了Bean的整个生命周期，对于ApplicationContext容器，所有的非懒加载的单例Bean都是在容器启动时创建的，因此Bean的整个生命周期的绝大多数过程都是在容器启动时发生的。容器启动时首先创建并初始化BeanFactory，随后执行一些特定的BeanFactoryPostProcessor（BeanFactory后置处理器）方法，其中就包括执行扫描包路径逻辑的ConfigurationClassPostProcessor类，今天的介绍也从该类开始。

![Bean的生命周期](https://github.com/ZhaoCharles/study-notes/blob/master/images/Bean的生命周期.png)

### 扫描生成BeanDefinition

ConfigurationClassPostProcessor类最主要的功能是解析配置类，扫描过程是在其解析配置类上的@ComponentScan注解后进行的，关于容器启动和解析配置类在其他部分已经详细介绍过，此处不再赘述。最终执行扫描逻辑的是ClassPathBeanDefinitionScanner类的doScan()方法。

```java
protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
   Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
    //遍历扫描从@ComponentScan注解得到的所有包路径
   for (String basePackage : basePackages) {
		//findCandidateComponents()方法执行扫描逻辑并将扫描到的Bean解析生成BeanDefinition
      Set<BeanDefinition> candidates = findCandidateComponents(basePackage);

      for (BeanDefinition candidate : candidates) {
         ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
         candidate.setScope(scopeMetadata.getScopeName());

         String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);

         if (candidate instanceof AbstractBeanDefinition) {
            postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
         }
         if (candidate instanceof AnnotatedBeanDefinition) {
            // 解析@Lazy、@Primary、@DependsOn、@Role、@Description
       AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
         }

         // 检查Spring容器中是否已经存在该beanName
         if (checkCandidate(beanName, candidate)) {
            BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
            definitionHolder =
                  AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
            beanDefinitions.add(definitionHolder);

            // 将生成的BeanDefinition注册到BeanFactory
            registerBeanDefinition(definitionHolder, this.registry);
         }
      }
   }
   return beanDefinitions;
}
```

doScan()方法主要是扫描从配置类上的@ComponentScan注解得到的所有包路径，将得到的Bean生成BeanDefinition，最后将其注册到BeanFactory中以便于后面随时对Bean进行实例化。此方法中的核心方法是findCandidateComponents()方法。

```java
public Set<BeanDefinition> findCandidateComponents(String basePackage) {
   if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
      return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
   }
   else {
      return scanCandidateComponents(basePackage);
   }
}
```

在findCandidateComponents()中，一般情况下进入的是scanCandidateComponents()方法。

```java
private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
   Set<BeanDefinition> candidates = new LinkedHashSet<>();
   try {
      // 获取basePackage下所有的文件资源
      String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
            resolveBasePackage(basePackage) + '/' + this.resourcePattern;
      Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
      boolean traceEnabled = logger.isTraceEnabled();
      boolean debugEnabled = logger.isDebugEnabled();
      for (Resource resource : resources) {
         if (traceEnabled) {
            logger.trace("Scanning " + resource);
         }
         if (resource.isReadable()) {
            try {
                //1.通过元数据读取器读取资源文件的元数据
               MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
               // 2.根据excludeFilters、includeFilters判断BeanDefinition是否是Bean
               if (isCandidateComponent(metadataReader)) { 
                  ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
                  sbd.setSource(resource);

                  if (isCandidateComponent(sbd)) {
                     if (debugEnabled) {
                        logger.debug("Identified candidate component class: " + resource);
                     }
                     candidates.add(sbd);
                  }
                  else {
                     if (debugEnabled) {
                        logger.debug("Ignored because not a concrete top-level class: " + resource);
                     }
                  }
               }
               else {
                  if (traceEnabled) {
                     logger.trace("Ignored because not matching any filter: " + resource);
                  }
               }
            }
            catch (Throwable ex) {
               throw new BeanDefinitionStoreException(
                     "Failed to read candidate component class: " + resource, ex);
            }
         }
         else {
            if (traceEnabled) {
               logger.trace("Ignored because not readable: " + resource);
            }
         }
      }
   }
   catch (IOException ex) {
      throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
   }
   return candidates;
}
```

1. 该方法首先获取到包路径下的所有资源文件（以Resource对象表示），然后用元数据读取器获取该类的一些信息（包括类名称、父类名称、实现接口名称、注解信息等等）。元数据读取器是利用ASM技术解析.class文件获取到的类的元数据，因此此时该类并没有被加载到JVM中，最终生成ScannedGenericBeanDefinition时BeanDefinition中保存的beanClass也是类的全限定名称而不是Class对象。
2. 此方法最主要的作用就是根据excludeFilters和includeFilters判断当前类是否是Bean，其中满足excludeFilters中任意一个过滤器条件的类都不是Bean，满足includeFilters中任意一个过滤器条件的都是Bean，在容器初始化时便将@Component注解添加到了includeFilters中，因此带有@Component注解的被认为是Bean。

最后得到所有的BeanDefinition并将其注册到BeanFactory中，扫描过程就完成了。

### Bean的生命周期

AnnotationConfigApplicationContext容器中根据BeanDefinition信息生成Bean并注册到BeanFactory单例池的过程发生在finishBeanFactoryInitialization()方法中。

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
   // 如果BeanFactory中存在名字叫conversionService的Bean,则设置为BeanFactory的conversionService属性
   // ConversionService是用来进行类型转化的
   if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
         beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
      beanFactory.setConversionService(
            beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
   }

   // 设置默认的占位符解析器  ${xxx}  ---key
   if (!beanFactory.hasEmbeddedValueResolver()) {
      beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
   }

   String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
   for (String weaverAwareName : weaverAwareNames) {
      getBean(weaverAwareName);
   }

   beanFactory.setTempClassLoader(null);

   beanFactory.freezeConfiguration();

   // 实例化非懒加载的单例Bean
   beanFactory.preInstantiateSingletons();
}
```

此方法中最核心的方法是preInstantiateSingletons()方法。

```java
public void preInstantiateSingletons() throws BeansException {
   List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

   // 遍历BeanFactory中全部的BeanDefinition
   for (String beanName : beanNames) {
      // 1.获取合并后的BeanDefinition
      RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
	 // 2.判断当前BeanDefinition是否是抽象的、单例的、懒加载的
      if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
          // 3.判断是否是FactoryBean
         if (isFactoryBean(beanName)) {
            // 获取FactoryBean对象
            Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
            if (bean instanceof FactoryBean) {
               FactoryBean<?> factory = (FactoryBean<?>) bean;
               boolean isEagerInit;
               if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                  isEagerInit = AccessController.doPrivileged(
                        (PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
                        getAccessControlContext());
               }
               else {
                  isEagerInit = (factory instanceof SmartFactoryBean &&
                        ((SmartFactoryBean<?>) factory).isEagerInit());
               }
               if (isEagerInit) {
                  // 创建真正的Bean对象(getObject()返回的对象)
                  getBean(beanName);
               }
            }
         }
         else {
            // 4.创建Bean对象
            getBean(beanName);
         }
      }
   }

   // 所有的非懒加载单例Bean都创建完了后执行SmartInitializingSingleton
   for (String beanName : beanNames) {
      Object singletonInstance = getSingleton(beanName);
      if (singletonInstance instanceof SmartInitializingSingleton) {
         StartupStep smartInitialize = this.getApplicationStartup().start("spring.beans.smart-initialize")
               .tag("beanName", beanName);
         SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
         if (System.getSecurityManager() != null) {
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
               smartSingleton.afterSingletonsInstantiated();
               return null;
            }, getAccessControlContext());
         }
         else {
            smartSingleton.afterSingletonsInstantiated();
         }
         smartInitialize.end();
      }
   }
}
```

1. 在getMergedLocalBeanDefinition()方法中，首先判断当前的BeanDefinition是否有父BeanDefinition（BeanDefinition是有继承关系的），如果有父BeanDefinition则将两个BeanDefinition合并为一个RootBeanDefinition对象返回（子BeanDefinition继承父BeanDefinition的属性，有冲突的属性子BeanDefinition覆盖父BeanDefinition）；如果没有则直接构造一个RootBeanDefinition对象返回。
2. 只有非抽象的BeanDefinition、单例且非懒加载的Bean才会直接创建，BeanDefinition也有抽象的，只是我们用得少，对于抽象的BeanDefinition是不能创建Bean对象的，其作用是用来给其他BeanDefinition继承的。
3. FactoryBean是一种特殊的Bean，其创建方式与一般的Bean也有些不同，关于FactoryBean在后面会单独介绍，目前只需要知道它是一种特殊的Bean即可。
4. 此处的getBean()方法就是我们在使用容器时获取Bean的getBean()方法，此方法除了获取Bean还可以创建Bean。

getBean()方法主要调用了doGetBean()方法。

```java
protected <T> T doGetBean(
      String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
      throws BeansException {

   // 1.将传入的name转换成beanName
   String beanName = transformedBeanName(name);
   Object beanInstance;

   // 2.根据beanName从单例池获取Bean
   Object sharedInstance = getSingleton(beanName);
   if (sharedInstance != null && args == null) {
      if (logger.isTraceEnabled()) {
         if (isSingletonCurrentlyInCreation(beanName)) {
            logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
                  "' that is not fully initialized yet - a consequence of a circular reference");
         }
         else {
            logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
         }
      }
      // 如果sharedInstance是FactoryBean，那么就调用getObject()返回对象
      beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
   }

   else {
      if (isPrototypeCurrentlyInCreation(beanName)) {
         throw new BeanCurrentlyInCreationException(beanName);
      }

      // 3.获取父BeanFactory，尝试从父容器中获取Bean对象
      BeanFactory parentBeanFactory = getParentBeanFactory();
      if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
         String nameToLookup = originalBeanName(name);
         if (parentBeanFactory instanceof AbstractBeanFactory) {
            return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                  nameToLookup, requiredType, args, typeCheckOnly);
         }
         else if (args != null) {
            return (T) parentBeanFactory.getBean(nameToLookup, args);
         }
         else if (requiredType != null) {
            return parentBeanFactory.getBean(nameToLookup, requiredType);
         }
         else {
            return (T) parentBeanFactory.getBean(nameToLookup);
         }
      }

      if (!typeCheckOnly) {
         markBeanAsCreated(beanName);
      }

      try {
         if (requiredType != null) {
            beanCreation.tag("beanType", requiredType::toString);
         }
         RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

         // 检查BeanDefinition是不是Abstract的
         checkMergedBeanDefinition(mbd, beanName, args);

         // 4.创建该Bean依赖的其他对象的Bean对象
         String[] dependsOn = mbd.getDependsOn();
         if (dependsOn != null) {
            for (String dep : dependsOn) {
               // beanName是不是被dep依赖了，如果是则出现了循环依赖
               if (isDependent(beanName, dep)) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                        "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
               }
               // dep被beanName依赖了，存入dependentBeanMap中，dep为key，beanName为value
               registerDependentBean(dep, beanName);

               // 创建所依赖的bean
               try {
                  getBean(dep);
               }
               catch (NoSuchBeanDefinitionException ex) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                        "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
               }
            }
         }

         // 5.创建当前Bean
         if (mbd.isSingleton()) {
            sharedInstance = getSingleton(beanName, () -> {
               try {
                  return createBean(beanName, mbd, args);
               }
               catch (BeansException ex) {
                  destroySingleton(beanName);
                  throw ex;
               }
            });
            beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
         }
         else if (mbd.isPrototype()) {
            Object prototypeInstance = null;
            try {
               beforePrototypeCreation(beanName);
               prototypeInstance = createBean(beanName, mbd, args);
            }
            finally {
               afterPrototypeCreation(beanName);
            }
            beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
         }
         else {
            String scopeName = mbd.getScope();
            if (!StringUtils.hasLength(scopeName)) {
               throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
            }
            Scope scope = this.scopes.get(scopeName);
            if (scope == null) {
               throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
            }
            try {  // session.getAttriute(beaName)  setAttri
               Object scopedInstance = scope.get(beanName, () -> {
                  beforePrototypeCreation(beanName);
                  try {
                     return createBean(beanName, mbd, args);
                  }
                  finally {
                     afterPrototypeCreation(beanName);
                  }
               });
               beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
            }
            catch (IllegalStateException ex) {
               throw new ScopeNotActiveException(beanName, scopeName, ex);
            }
         }
      }
      catch (BeansException ex) {
         beanCreation.tag("exception", ex.getClass().toString());
         beanCreation.tag("message", String.valueOf(ex.getMessage()));
         cleanupAfterBeanCreationFailure(beanName);
         throw ex;
      }
      finally {
         beanCreation.end();
      }
   }

   // 检查通过name所获得到的beanInstance的类型是否是requiredType
   return adaptBeanInstance(name, beanInstance, requiredType);
}
```

1. 此处的name有可能是表示FactoryBean的以&开头的名称（FactoryBean部分再详细介绍），有可能是正常的beanName，也有可能是别名。BeanFactory保存有一个别名和beanName对应的映射，因此可以通过别名获取到beanName。
2. 单例池是一个以beanName为key，Bean对象为value的BeanFactory中的键值对，容器中创建的所有单例Bean都存储到单例池中。
3. 容器有父子关系，对于子容器来说可以获取和注入父容器的Bean。此处该BeanDefinition不存在于子容器中并且有父容器时，就会尝试使用父容器来生成Bean。
4. BeanDefinition中有个属性保存了该Bean依赖的其他Bean的beanName，在实例化该Bean对象时需要先将其依赖的Bean对象全部生成，这个过程会存在循环依赖的问题，关于循环依赖也会有单独的部分介绍。
5. 最后根据该Bean的作用域的不同使用不同的逻辑创建Bean对象，但最终都是使用creatBean()方法创建对象。

creatBean()方法的主要作用是执行实例化前的后置处理器方法以及后续的实例化Bean的过程。

```java
protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
      throws BeanCreationException {

   RootBeanDefinition mbdToUse = mbd;

   // 判断当前Bean的Class对象是否加载。如果未加载则加载
   Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
   if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
      mbdToUse = new RootBeanDefinition(mbd);
      mbdToUse.setBeanClass(resolvedClass);
   }

   try {
      mbdToUse.prepareMethodOverrides();
   }
   catch (BeanDefinitionValidationException ex) {
      throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
            beanName, "Validation of method overrides failed", ex);
   }

   try {
      // 实例化前的BeanPostProcessor方法调用
      Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
      if (bean != null) {
         return bean;
      }
   }
   catch (Throwable ex) {
      throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
            "BeanPostProcessor before instantiation of bean failed", ex);
   }

   try {
       // 实例化Bean的方法
      Object beanInstance = doCreateBean(beanName, mbdToUse, args);
      if (logger.isTraceEnabled()) {
         logger.trace("Finished creating instance of bean '" + beanName + "'");
      }
      return beanInstance;
   }
   catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
      throw ex;
   }
   catch (Throwable ex) {
      throw new BeanCreationException(
            mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
   }
}
```

在createBean()方法中出现了第一个Bean后置处理器的调用，即resolveBeforeInstantiation()方法，该方法最终调用了InstantiationAwareBeanPostProcessor后置处理器的postProcessBeforeInstantiation()方法，AOP中创建代理对象的操作就是在此处进行的。

接下来进入doCreateBean()方法，该方法主要的作用是创建Bean对象、属性填充以及Bean对象的初始化（其实就是完成Bean创建的后续部分）。由于该方法较复杂，因此我们使用①②③这样的数字来表示其内部某个方法。

```java
protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
      throws BeanCreationException {

   BeanWrapper instanceWrapper = null;
   if (mbd.isSingleton()) {
      // 有可能在当前Bean创建之前，就有其他Bean把当前Bean给创建出来了（比如依赖注入过程中）
      instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
   }
   if (instanceWrapper == null) {
      // ①方法创建Bean实例
      instanceWrapper = createBeanInstance(beanName, mbd, args);
   }
   Object bean = instanceWrapper.getWrappedInstance();
   Class<?> beanType = instanceWrapper.getWrappedClass();
   if (beanType != NullBean.class) {
      mbd.resolvedTargetType = beanType;
   }

   synchronized (mbd.postProcessingLock) {
      if (!mbd.postProcessed) {
         try {
             // ②方法处理BeanDefinition
            applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
         }
         catch (Throwable ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                  "Post-processing of merged bean definition failed", ex);
         }
         mbd.postProcessed = true;
      }
   }

   // 为了解决循环依赖提前缓存单例创建工厂
   boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
         isSingletonCurrentlyInCreation(beanName));
   if (earlySingletonExposure) {
      if (logger.isTraceEnabled()) {
         logger.trace("Eagerly caching bean '" + beanName +
               "' to allow for resolving potential circular references");
      }
      // 循环依赖-添加到三级缓存
      addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
   }

   Object exposedObject = bean;
   try {
      // ③方法属性填充
      populateBean(beanName, mbd, instanceWrapper);

      // ④方法初始化
      exposedObject = initializeBean(beanName, exposedObject, mbd);
   }
   catch (Throwable ex) {
      if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
         throw (BeanCreationException) ex;
      }
      else {
         throw new BeanCreationException(
               mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
      }
   }

   if (earlySingletonExposure) {
      Object earlySingletonReference = getSingleton(beanName, false);
      if (earlySingletonReference != null) {
         if (exposedObject == bean) {
            exposedObject = earlySingletonReference;
         }
         else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
            // beanName被哪些bean依赖了，现在发现beanName所对应的bean对象发生了改变，那么则会报错
            String[] dependentBeans = getDependentBeans(beanName);
            Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
            for (String dependentBean : dependentBeans) {
               if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                  actualDependentBeans.add(dependentBean);
               }
            }
            if (!actualDependentBeans.isEmpty()) {
               throw new BeanCurrentlyInCreationException(beanName,
                     "Bean with name '" + beanName + "' has been injected into other beans [" +
                     StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
                     "] in its raw version as part of a circular reference, but has eventually been " +
                     "wrapped. This means that said other beans do not use the final version of the " +
                     "bean. This is often the result of over-eager type matching - consider using " +
                     "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
            }
         }
      }
   }

   try {
      registerDisposableBeanIfNecessary(beanName, bean, mbd);
   }
   catch (BeanDefinitionValidationException ex) {
      throw new BeanCreationException(
            mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
   }

   return exposedObject;
}
```

进入方法①createBeanInstance()，该方法主要的作用是推断出合适的构造方法并使用该方法实例化对象返回，关于推断构造方法后续会单独介绍，此处仅介绍createBeanInstance()方法执行的Bean后置处理器。该方法主要执行后置处理器SmartInstantiationAwareBeanPostProcessor的determineCandidateConstructors()方法，该方法（AutowiredAnnotationBeanPostProcessor类的实现）包含了构造推断方法的主要逻辑。

进入方法②applyMergedBeanDefinitionPostProcessors()，该方法内仅执行后置处理器MergedBeanDefinitionPostProcessor的postProcessMergedBeanDefinition()方法，主要用来处理合并后的BeanDefinition。

```java
protected void  applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
   for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
      processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
   }
}
```

回到doCreateBean()方法，下面的一段代码主要是用来处理循环依赖，循环依赖部分同样会单独介绍，此处不赘述。

进入方法③populateBean()，该方法的主要作用是完成Spring byName或byType的属性注入和执行了两个Bean后置处理器方法：InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation()方法以及InstantiationAwareBeanPostProcessor的postProcessProperties()方法，其中postProcessProperties()（AutowiredAnnotationBeanPostProcessor类的实现）的作用是解析@Autowired、@Value等注解寻找注入点并保存。

进入方法④initializeBean()，该方法的主要作用是执行一些Aware接口方法以及完成初始化前、初始化和初始化后的工作。

```java
protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
   if (System.getSecurityManager() != null) {
      AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
         invokeAwareMethods(beanName, bean);
         return null;
      }, getAccessControlContext());
   }
   else {
      //执行几个Aware接口回调方法
      invokeAwareMethods(beanName, bean);
   }

   Object wrappedBean = bean;

   // 1.初始化前
   if (mbd == null || !mbd.isSynthetic()) {
      wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
   }

   // 2.初始化
   try {
      invokeInitMethods(beanName, wrappedBean, mbd);
   }
   catch (Throwable ex) {
      throw new BeanCreationException(
            (mbd != null ? mbd.getResourceDescription() : null),
            beanName, "Invocation of init method failed", ex);
   }

   // 3.初始化后
   if (mbd == null || !mbd.isSynthetic()) {
      wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
   }

   return wrappedBean;
}
```

1. 初始化前执行了后置处理器BeanPostProcessor的postProcessBeforeInitialization()方法。
2. 某个Bean如果实现了InitializingBean接口，在初始化这一步就会执行该Bean实现的InitializingBean的afterPropertiesSet()方法。
3. 初始化后执行了后置处理器BeanPostProcessor的postProcessAfterInitialization()方法。

初始化完成后，回到最初的doGetBean()方法，将生成的Bean对象放入BeanFactory的单例池中。至此，所有的非懒加载的单例Bean都已经被实例化初始化完成，随时可以被getBean()方法获取使用。

Bean的生命周期还包括Bean的销毁过程，销毁过程是在Spring容器关闭时发生的。Spring容器关闭的方法是close()方法，该方法又调用了doClose()方法。

```java
protected void doClose() {
   if (this.active.get() && this.closed.compareAndSet(false, true)) {

      try {
         // 发布容器关闭事件
         publishEvent(new ContextClosedEvent(this));
      }
      catch (Throwable ex) {
         logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
      }

      // 将容器设置为关闭状态
      if (this.lifecycleProcessor != null) {
         try {
            this.lifecycleProcessor.onClose();
         }
         catch (Throwable ex) {
            logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
         }
      }

      // 销毁单例池以及其他包括依赖Map、别名Map等等辅助的集合
      destroyBeans();

      // 关闭BeanFactory（设置BeanFactory的serializationId=null）
      closeBeanFactory();

      // 模板方法
      onClose();

      if (this.earlyApplicationListeners != null) {
         this.applicationListeners.clear();
         this.applicationListeners.addAll(this.earlyApplicationListeners);
      }

      this.active.set(false);
   }
}
```

至此，Bean的生命周期的大概过程就介绍完了。

### Bean的后置处理器在Bean的生命周期中的执行顺序

1. 实例化前： InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation()方法
2. 推断构造方法： SmartInstantiationAwareBeanPostProcessor的determineCandidateConstructors()方法
3. 实例化
4. 处理BeanDefinition： MergedBeanDefinitionPostProcessor的postProcessMergedBeanDefinition()方法
5. 实例化后： InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation()方法
6. 自动注入
7. 填充属性： InstantiationAwareBeanPostProcessor的postProcessProperties()方法
8. 执行Aware回调方法
9. 初始化前： BeanPostProcessor的postProcessBeforeInitialization()方法
10. 初始化：执行InitializingBean()的afterPropertiesSet()方法
11. 初始化后： BeanPostProcessor的postProcessAfterInitialization()方法

