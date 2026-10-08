package com.oct.hidepill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HideNavPill: ";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 1. Hook Launcher / Taskbar (Nexus Launcher & Launcher3)
        if (lpparam.packageName.equals("com.google.android.apps.nexuslauncher") ||
            lpparam.packageName.equals("com.android.launcher3")) {
            hookLauncher(lpparam);
        }

        // 2. Hook SystemUI
        if (lpparam.packageName.equals("com.android.systemui")) {
            hookSystemUI(lpparam);
        }

        // 3. Hook IME processes (e.g. Gboard and any InputMethodService)
        hookInputMethod(lpparam);
    }

    private void hookLauncher(LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "Initializing launcher hooks for " + lpparam.packageName);

        // A. Hook StashedHandleView: completely disable rendering and background
        try {
            Class<?> stashedHandleViewClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.taskbar.StashedHandleView", lpparam.classLoader);
            if (stashedHandleViewClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "onDraw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "draw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "setVisibility", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = View.GONE;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "updateHandleColor", boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "setBackgroundColor", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = Color.TRANSPARENT;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(stashedHandleViewClass, "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View v = (View) param.thisObject;
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
                        }
                    });
                } catch (Throwable ignored) {}

                XposedBridge.log(TAG + "StashedHandleView successfully hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking StashedHandleView: " + t);
        }

        // B. Hook StashedHandleViewController to zero out handle dimensions
        try {
            Class<?> controllerClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.taskbar.StashedHandleViewController", lpparam.classLoader);
            if (controllerClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(controllerClass, "getNavHandleWidth", android.content.Context.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return 0;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(controllerClass, "updateSamplingState", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedHelpers.setIntField(param.thisObject, "mStashedHandleHeight", 0);
                                XposedHelpers.setIntField(param.thisObject, "mStashedHandleWidth", 0);
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}

                XposedBridge.log(TAG + "StashedHandleViewController successfully hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking StashedHandleViewController: " + t);
        }

        // C. Hook TaskbarDragLayer init
        try {
            Class<?> dragLayerClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.taskbar.TaskbarDragLayer", lpparam.classLoader);
            if (dragLayerClass != null) {
                XposedBridge.hookAllMethods(dragLayerClass, "init", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object renderer = XposedHelpers.getObjectField(param.thisObject, "mBackgroundRenderer");
                            if (renderer != null) {
                                XposedHelpers.setIntField(renderer, "stashedHandleWidth", 0);
                                XposedHelpers.setIntField(renderer, "stashedHandleHeight", 0);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
                XposedBridge.log(TAG + "TaskbarDragLayer successfully hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking TaskbarDragLayer: " + t);
        }

        // D. Hook NavbarButtonsViewController to suppress back button (IME down arrow) & ime switcher button
        try {
            Class<?> navButtonsControllerClass = XposedHelpers.findClassIfExists(
                "com.android.launcher3.taskbar.NavbarButtonsViewController", lpparam.classLoader);
            if (navButtonsControllerClass != null) {
                // Hook addButton to hide and clear back button / ime switcher button when not in 3-button nav
                XposedBridge.hookAllMethods(navButtonsControllerClass, "addButton", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            int buttonType = (int) param.args[1];
                            // buttonType 1 = back button (down arrow on keyboard), 8 = ime switcher
                            if (buttonType == 1 || buttonType == 8) {
                                Object contextObj = XposedHelpers.getObjectField(param.thisObject, "mContext");
                                boolean isThreeButtonNav = false;
                                if (contextObj != null) {
                                    try {
                                        isThreeButtonNav = (boolean) XposedHelpers.callMethod(contextObj, "isThreeButtonNav");
                                    } catch (Throwable ignored) {}
                                }
                                if (!isThreeButtonNav) {
                                    ImageView btn = (ImageView) param.getResult();
                                    if (btn != null) {
                                        btn.setVisibility(View.GONE);
                                        btn.setAlpha(0f);
                                        btn.setImageDrawable(null);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });

                XposedBridge.log(TAG + "NavbarButtonsViewController.addButton hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NavbarButtonsViewController: " + t);
        }
    }

    private void hookSystemUI(LoadPackageParam lpparam) {
        XposedBridge.log(TAG + "Initializing SystemUI hooks");

        // A. Hook NavigationHandle in SystemUI
        try {
            Class<?> navHandleClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.navigationbar.gestural.NavigationHandle", lpparam.classLoader);
            if (navHandleClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(navHandleClass, "onDraw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(navHandleClass, "draw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(navHandleClass, "setVisibility", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = View.GONE;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(navHandleClass, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = 0f;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(navHandleClass, "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View v = (View) param.thisObject;
                            v.setVisibility(View.GONE);
                            v.setAlpha(0f);
                        }
                    });
                } catch (Throwable ignored) {}

                XposedBridge.log(TAG + "NavigationHandle successfully hooked");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NavigationHandle in SystemUI: " + t);
        }

        // B. Hook NavigationBarInflaterView to remove back button and ime switcher in gesture mode
        try {
            Class<?> inflaterViewClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.navigationbar.views.NavigationBarInflaterView", lpparam.classLoader);
            if (inflaterViewClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(inflaterViewClass, "getDefaultLayout", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                boolean isGestural = false;
                                int navBarMode = XposedHelpers.getIntField(param.thisObject, "mNavBarMode");
                                // 2 = QuickStepContract.NAV_BAR_MODE_GESTURAL
                                if (navBarMode == 2) {
                                    isGestural = true;
                                }
                                if (isGestural) {
                                    // Default was "back[70AC];home_handle;ime_switcher[70AC]"
                                    // Return empty or handle only
                                    param.setResult(";home_handle;");
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log(TAG + "SystemUI NavigationBarInflaterView.getDefaultLayout hooked");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NavigationBarInflaterView: " + t);
        }
    }

    private void hookInputMethod(LoadPackageParam lpparam) {
        // A. Hook NavigationBarInflaterView inside android.inputmethodservice.navigationbar
        try {
            Class<?> imeInflaterClass = XposedHelpers.findClassIfExists(
                "android.inputmethodservice.navigationbar.NavigationBarInflaterView", lpparam.classLoader);
            if (imeInflaterClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(imeInflaterClass, "getDefaultLayout", new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            // Suppress back[70AC] and ime_switcher[70AC]
                            return ";home_handle;";
                        }
                    });
                    XposedBridge.log(TAG + "android.inputmethodservice.navigationbar.NavigationBarInflaterView.getDefaultLayout replaced for " + lpparam.packageName);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // B. Hook NavigationBarView inside android.inputmethodservice.navigationbar
        try {
            Class<?> imeNavBarViewClass = XposedHelpers.findClassIfExists(
                "android.inputmethodservice.navigationbar.NavigationBarView", lpparam.classLoader);
            if (imeNavBarViewClass != null) {
                // In updateNavButtonIcons, force back button & ime switcher visibility to GONE
                try {
                    XposedHelpers.findAndHookMethod(imeNavBarViewClass, "updateNavButtonIcons", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object backDispatcher = XposedHelpers.callMethod(param.thisObject, "getBackButton");
                                if (backDispatcher != null) {
                                    XposedHelpers.callMethod(backDispatcher, "setVisibility", View.GONE);
                                }
                                Object imeDispatcher = XposedHelpers.callMethod(param.thisObject, "getImeSwitchButton");
                                if (imeDispatcher != null) {
                                    XposedHelpers.callMethod(imeDispatcher, "setVisibility", View.GONE);
                                }
                                Object homeHandle = XposedHelpers.callMethod(param.thisObject, "getHomeHandle");
                                if (homeHandle != null) {
                                    XposedHelpers.callMethod(homeHandle, "setVisibility", View.GONE);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    XposedBridge.log(TAG + "NavigationBarView.updateNavButtonIcons hooked for " + lpparam.packageName);
                } catch (Throwable ignored) {}

                // Also hook orientBackButton to no-op
                try {
                    Class<?> drawableClass = XposedHelpers.findClassIfExists(
                        "android.inputmethodservice.navigationbar.KeyButtonDrawable", lpparam.classLoader);
                    if (drawableClass != null) {
                        XposedHelpers.findAndHookMethod(imeNavBarViewClass, "orientBackButton", drawableClass, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                return null;
                            }
                        });
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // C. Hook NavigationHandle inside android.inputmethodservice.navigationbar (in case IME draws pill)
        try {
            Class<?> imeNavHandleClass = XposedHelpers.findClassIfExists(
                "android.inputmethodservice.navigationbar.NavigationHandle", lpparam.classLoader);
            if (imeNavHandleClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(imeNavHandleClass, "onDraw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(imeNavHandleClass, "draw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(imeNavHandleClass, "setVisibility", int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = View.GONE;
                        }
                    });
                } catch (Throwable ignored) {}

                try {
                    XposedHelpers.findAndHookMethod(imeNavHandleClass, "setAlpha", float.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = 0f;
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // D. Hook KeyButtonView inside android.inputmethodservice.navigationbar
        try {
            Class<?> keyButtonViewClass = XposedHelpers.findClassIfExists(
                "android.inputmethodservice.navigationbar.KeyButtonView", lpparam.classLoader);
            if (keyButtonViewClass != null) {
                try {
                    XposedHelpers.findAndHookMethod(keyButtonViewClass, "draw", Canvas.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            View view = (View) param.thisObject;
                            // Check if this is the back button (0x1020382 / R.id.back) or ime_switcher
                            int id = view.getId();
                            // android.R.id.back = 16909186 (0x1020382)
                            if (id == 0x1020382 || id == 0x1020388) {
                                view.setVisibility(View.GONE);
                                view.setAlpha(0f);
                                param.setResult(null);
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
