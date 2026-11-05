declare namespace JSX {
  interface Element {
    type: any;
    props: any;
    key: any;
  }
  interface IntrinsicElements {
    [elemName: string]: any;
  }
}

declare namespace React {
  export type ReactNode =
    | ReactElement<any, any>
    | string
    | number
    | Iterable<ReactNode>
    | boolean
    | null
    | undefined;

  export type ComponentType<P = {}> = ComponentClass<P> | FC<P>;
  export interface FC<P = {}> {
    (props: PropsWithChildren<P>, context?: any): ReactElement<any, any> | null;
    displayName?: string;
  }

  export type PropsWithChildren<P = {}> = P & {
    children?: ReactNode | undefined;
    key?: string | number | null | undefined;
  };

  export interface ReactElement<P = any, T = any> {
    type: T;
    props: P;
    key: string | null;
  }

  export class Component<P = {}, S = {}> {
    constructor(props: P);
    state: S;
    props: P;
    setState(state: any): void;
    render(): ReactNode;
  }

  export interface ComponentClass<P = {}, S = {}> {
    new (props: P, context?: any): Component<P, S>;
  }

  export function createElement<P extends {}>(
    type: any,
    props?: any,
    ...children: any[]
  ): ReactElement<P>;

  export function useState<T>(initialState: T | (() => T)): [T, Dispatch<SetStateAction<T>>];
  export function useEffect(effect: () => void | (() => void), deps?: readonly any[]): void;
  export function useLayoutEffect(effect: () => void | (() => void), deps?: readonly any[]): void;
  export function useCallback<T extends (...args: any[]) => any>(callback: T, deps: readonly any[]): T;
  export function useMemo<T>(factory: () => T, deps: readonly any[] | undefined): T;
  export function useRef<T>(initialValue: T): { current: T };
  export function useRef<T>(initialValue: T | null): { current: T | null };
  export function useRef<T = undefined>(): { current: T | undefined };

  export type Dispatch<A> = (value: A) => void;
  export type SetStateAction<S> = S | ((prevState: S) => S);

  export interface Context<T> {
    Provider: FC<{ value: T; children?: ReactNode }>;
    Consumer: FC<{ children: (value: T) => ReactNode }>;
    displayName?: string;
  }

  export function createContext<T>(defaultValue: T): Context<T>;
  export function useContext<T>(context: Context<T>): T;
  export const Fragment: FC<{ children?: ReactNode }>;
}

declare module 'react' {
  export = React;
}

declare module 'react/jsx-runtime' {
  export const jsx: (type: any, props: any, key?: any) => any;
  export const jsxs: (type: any, props: any, key?: any) => any;
  export const Fragment: any;
}

declare module 'react-native' {
  export type FlexAlignType = 'flex-start' | 'flex-end' | 'center' | 'stretch' | 'baseline';

  export interface ViewStyle {
    flex?: number;
    flexDirection?: 'row' | 'column' | 'row-reverse' | 'column-reverse';
    justifyContent?: 'flex-start' | 'flex-end' | 'center' | 'space-between' | 'space-around' | 'space-evenly';
    alignItems?: FlexAlignType;
    alignSelf?: 'auto' | FlexAlignType;
    flexWrap?: 'wrap' | 'nowrap' | 'wrap-reverse';
    flexGrow?: number;
    flexShrink?: number;
    flexBasis?: number | string;
    gap?: number;
    rowGap?: number;
    columnGap?: number;
    padding?: number;
    paddingTop?: number;
    paddingRight?: number;
    paddingBottom?: number;
    paddingLeft?: number;
    paddingHorizontal?: number;
    paddingVertical?: number;
    margin?: number;
    marginTop?: number;
    marginRight?: number;
    marginBottom?: number;
    marginLeft?: number;
    marginHorizontal?: number;
    marginVertical?: number;
    width?: number | string;
    height?: number | string;
    minWidth?: number | string;
    maxWidth?: number | string;
    minHeight?: number | string;
    maxHeight?: number | string;
    backgroundColor?: string;
    borderWidth?: number;
    borderTopWidth?: number;
    borderRightWidth?: number;
    borderBottomWidth?: number;
    borderLeftWidth?: number;
    borderColor?: string;
    borderRadius?: number;
    borderTopLeftRadius?: number;
    borderTopRightRadius?: number;
    borderBottomLeftRadius?: number;
    borderBottomRightRadius?: number;
    overflow?: 'visible' | 'hidden' | 'scroll';
    position?: 'absolute' | 'relative';
    top?: number | string;
    right?: number | string;
    bottom?: number | string;
    left?: number | string;
    zIndex?: number;
    opacity?: number | Animated.Value;
    elevation?: number;
    shadowColor?: string;
    shadowOffset?: { width: number; height: number };
    shadowOpacity?: number;
    shadowRadius?: number;
    display?: 'none' | 'flex';
    transform?: any[];
  }

  export interface TextStyle extends ViewStyle {
    color?: string;
    fontFamily?: string;
    fontSize?: number;
    fontStyle?: 'normal' | 'italic';
    fontWeight?: 'normal' | 'bold' | '100' | '200' | '300' | '400' | '500' | '600' | '700' | '800' | '900';
    letterSpacing?: number;
    lineHeight?: number;
    textAlign?: 'auto' | 'left' | 'right' | 'center' | 'justify';
    textDecorationLine?: 'none' | 'underline' | 'line-through' | 'underline line-through';
    textTransform?: 'none' | 'uppercase' | 'lowercase' | 'capitalize';
  }

  export interface ImageStyle extends ViewStyle {
    resizeMode?: 'cover' | 'contain' | 'stretch' | 'repeat' | 'center';
    tintColor?: string;
    overlayColor?: string;
  }

  export type StyleProp<T> = T | null | undefined | false | (StyleProp<T>)[];

  export interface ViewProps {
    key?: string | number | null | undefined;
    style?: StyleProp<ViewStyle>;
    children?: React.ReactNode;
    accessible?: boolean;
    accessibilityLabel?: string;
    accessibilityRole?: string;
    accessibilityHint?: string;
    accessibilityState?: { selected?: boolean; disabled?: boolean; [key: string]: any };
    pointerEvents?: 'box-none' | 'none' | 'box-only' | 'auto';
    testID?: string;
  }

  export interface TextProps {
    key?: string | number | null | undefined;
    style?: StyleProp<TextStyle>;
    children?: React.ReactNode;
    numberOfLines?: number;
    ellipsizeMode?: 'head' | 'middle' | 'tail' | 'clip';
    accessible?: boolean;
    accessibilityLabel?: string;
    accessibilityRole?: string;
    testID?: string;
  }

  export interface TouchableOpacityProps extends ViewProps {
    onPress?: () => void;
    activeOpacity?: number;
    disabled?: boolean;
  }

  export interface PressableProps extends ViewProps {
    onPress?: () => void;
    disabled?: boolean;
    style?: StyleProp<ViewStyle> | ((state: { pressed: boolean }) => StyleProp<ViewStyle>);
  }

  export interface ScrollViewProps extends ViewProps {
    contentContainerStyle?: StyleProp<ViewStyle>;
    horizontal?: boolean;
    showsHorizontalScrollIndicator?: boolean;
    showsVerticalScrollIndicator?: boolean;
    keyboardShouldPersistTaps?: 'always' | 'never' | 'handled';
  }

  export interface TextInputProps {
    key?: string | number | null | undefined;
    style?: StyleProp<TextStyle>;
    value?: string;
    onChangeText?: (text: string) => void;
    placeholder?: string;
    placeholderTextColor?: string;
    autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
    autoCorrect?: boolean;
    secureTextEntry?: boolean;
    keyboardType?: 'default' | 'email-address' | 'numeric' | 'phone-pad';
    multiline?: boolean;
    numberOfLines?: number;
    editable?: boolean;
    testID?: string;
  }

  export interface ModalProps {
    key?: string | number | null | undefined;
    visible?: boolean;
    transparent?: boolean;
    animationType?: 'none' | 'slide' | 'fade';
    onRequestClose?: () => void;
    children?: React.ReactNode;
  }

  export interface ActivityIndicatorProps extends ViewProps {
    size?: 'small' | 'large' | number;
    color?: string;
    animating?: boolean;
  }

  export const View: React.FC<ViewProps>;
  export const Text: React.FC<TextProps>;
  export const TouchableOpacity: React.FC<TouchableOpacityProps>;
  export const Pressable: React.FC<PressableProps>;
  export const ScrollView: React.FC<ScrollViewProps>;
  export const SafeAreaView: React.FC<ViewProps>;
  export const TextInput: React.FC<TextInputProps>;
  export const Modal: React.FC<ModalProps>;
  export const ActivityIndicator: React.FC<ActivityIndicatorProps>;

  export namespace Animated {
    export class Value {
      constructor(value: number);
      setValue(value: number): void;
    }
    export function timing(value: Value, config: { toValue: number; duration: number; useNativeDriver: boolean }): { start: () => void };
    export function parallel(animations: unknown[]): { start: () => void; stop: () => void };
    export function sequence(animations: unknown[]): unknown;
    export function loop(animation: unknown): { start: () => void; stop: () => void };
    export const View: React.FC<ViewProps>;
  }

  export namespace StyleSheet {
    export function create<T extends Record<string, ViewStyle | TextStyle | ImageStyle>>(styles: T): T;
    export function flatten<T>(style: StyleProp<T>): T;
  }

  export namespace Dimensions {
    export function get(dim: 'window' | 'screen'): { width: number; height: number; scale: number; fontScale: number };
  }

  export namespace Platform {
    export const OS: 'ios' | 'android' | 'web' | 'windows' | 'macos';
    export const select: <T>(obj: { ios?: T; android?: T; default?: T }) => T;
  }
}

declare module 'expo-status-bar' {
  export interface StatusBarProps {
    style?: 'auto' | 'inverted' | 'light' | 'dark';
    backgroundColor?: string;
    translucent?: boolean;
    hidden?: boolean;
  }
  export const StatusBar: React.FC<StatusBarProps>;
}
