import React from 'react';
import { BrowserRouter as Router, Route, Switch } from 'react-router-dom';
import create from 'zustand';

const useStore = create(set => ({ count: 0, inc: () => set(state => ({ count: state.count + 1 })) }));

function Home() {
  const { count, inc } = useStore();
  return (
    <div>
      <h1>Porcana Frontend</h1>
      <p>Count: {count}</p>
      <button onClick={inc}>Increment</button>
    </div>
  );
}

function App() {
  return (
    <Router>
      <Switch>
        <Route path="/" component={Home} />
      </Switch>
    </Router>
  );
}

export default App;
